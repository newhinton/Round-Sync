// Package internxt provides authentication handling
package internxt

import (
	"context"
	"crypto/hmac"
	"crypto/sha1"
	"crypto/sha256"
	"encoding/base32"
	"encoding/base64"
	"encoding/binary"
	"encoding/hex"
	"errors"
	"fmt"
	"math"
	mrand "math/rand"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
	internxtauth "github.com/internxt/rclone-adapter/auth"
	internxtconfig "github.com/internxt/rclone-adapter/config"
	sdkerrors "github.com/internxt/rclone-adapter/errors"
	"github.com/rclone/rclone/fs"
	"github.com/rclone/rclone/fs/config/obscure"
	"github.com/rclone/rclone/fs/fshttp"
	"github.com/rclone/rclone/lib/oauthutil"
	"golang.org/x/oauth2"
)

type userInfo struct {
	RootFolderID string
	Bucket       string
	BridgeUser   string
	UserID       string
}

type userInfoConfig struct {
	Token string
}

// getUserInfo fetches user metadata from the refresh endpoint
func getUserInfo(ctx context.Context, cfg *userInfoConfig) (*userInfo, error) {
	// Call the refresh endpoint to get all user metadata
	refreshCfg := internxtconfig.NewDefaultToken(cfg.Token)
	resp, err := internxtauth.RefreshToken(ctx, refreshCfg)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch user info: %w", err)
	}

	if resp.User.Bucket == "" {
		return nil, errors.New("API response missing user.bucket")
	}
	if resp.User.RootFolderID == "" {
		return nil, errors.New("API response missing user.rootFolderId")
	}
	if resp.User.BridgeUser == "" {
		return nil, errors.New("API response missing user.bridgeUser")
	}
	if resp.User.UserID == "" {
		return nil, errors.New("API response missing user.userId")
	}

	info := &userInfo{
		RootFolderID: resp.User.RootFolderID,
		Bucket:       resp.User.Bucket,
		BridgeUser:   resp.User.BridgeUser,
		UserID:       resp.User.UserID,
	}

	fs.Debugf(nil, "User info: rootFolderId=%s, bucket=%s",
		info.RootFolderID, info.Bucket)

	return info, nil
}

// parseJWTExpiry extracts the expiry time from a JWT token string
func parseJWTExpiry(tokenString string) (time.Time, error) {
	parser := jwt.NewParser(jwt.WithoutClaimsValidation())
	token, _, err := parser.ParseUnverified(tokenString, jwt.MapClaims{})
	if err != nil {
		return time.Time{}, fmt.Errorf("failed to parse token: %w", err)
	}

	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok {
		return time.Time{}, errors.New("invalid token claims")
	}

	exp, ok := claims["exp"].(float64)
	if !ok {
		return time.Time{}, errors.New("token missing expiration")
	}

	return time.Unix(int64(exp), 0), nil
}

// jwtToOAuth2Token converts a JWT string to an oauth2.Token with expiry
func jwtToOAuth2Token(jwtString string) (*oauth2.Token, error) {
	expiry, err := parseJWTExpiry(jwtString)
	if err != nil {
		return nil, err
	}

	return &oauth2.Token{
		AccessToken: jwtString,
		TokenType:   "Bearer",
		Expiry:      expiry,
	}, nil
}

// computeBasicAuthHeader creates the BasicAuthHeader for bucket operations
// Following the pattern from SDK's auth/access.go:96-102
func computeBasicAuthHeader(bridgeUser, userID string) string {
	sum := sha256.Sum256([]byte(userID))
	hexPass := hex.EncodeToString(sum[:])
	creds := fmt.Sprintf("%s:%s", bridgeUser, hexPass)
	return "Basic " + base64.StdEncoding.EncodeToString([]byte(creds))
}

// refreshJWTToken refreshes the token using Internxt's refresh endpoint
func (f *Fs) refreshJWTToken(ctx context.Context) error {
	currentToken, err := oauthutil.GetToken(f.name, f.m)
	if err != nil {
		return fmt.Errorf("failed to get current token from config: %w", err)
	}

	cfg := internxtconfig.NewDefaultToken(currentToken.AccessToken)
	resp, err := internxtauth.RefreshToken(ctx, cfg)
	if err != nil {
		return err
	}

	useToken := resp.NewToken
	if useToken == "" {
		useToken = resp.Token
	}

	if useToken == "" {
		return errors.New("refresh response missing token")
	}

	// Convert JWT to oauth2.Token format
	token, err := jwtToOAuth2Token(useToken)
	if err != nil {
		return fmt.Errorf("failed to parse refreshed token: %w", err)
	}

	err = oauthutil.PutToken(f.name, f.m, token, false)
	if err != nil {
		return fmt.Errorf("failed to save token to config: %w", err)
	}

	f.cfg.Token = useToken
	if resp.User.Bucket != "" {
		f.m.Set("bucket", resp.User.Bucket)
		f.cfg.Bucket = resp.User.Bucket
	}
	if resp.User.RootFolderID != "" {
		f.cfg.RootFolderID = resp.User.RootFolderID
	}

	fs.Debugf(f.name, "Token refreshed successfully, new expiry: %v", token.Expiry)
	return nil
}

// reLogin performs a full re-login using stored email+password credentials.
// Returns the AccessResponse on success, or an error if 2FA is required or login fails.
func (f *Fs) reLogin(ctx context.Context) (*internxtauth.AccessResponse, error) {
	password, err := obscure.Reveal(f.opt.Pass)
	if err != nil {
		return nil, fmt.Errorf("couldn't decrypt password: %w", err)
	}

	cfg := internxtconfig.NewDefaultToken("")
	cfg.HTTPClient = fshttp.NewClient(ctx)

	fs.Debugf(f, "Triggering DoLogin fallback for email: %q (password length: %d)", f.opt.Email, len(password))

	// Call DoLogin directly — it handles Login+hash+Access atomically internally.
	// We must NOT pre-call Login ourselves, because DoLogin always calls Login again
	// to get a fresh sKey. A double-Login fetches two different sKeys, making the
	// password hash produced by DoLogin's internal Login mismatch what Access expects.
	resp, loginErr := internxtauth.DoLogin(ctx, cfg, f.opt.Email, password, "")
	if loginErr == nil {
		return resp, nil
	}
	if loginErr.Error() != "2FA code required" {
		return nil, fmt.Errorf("re-login failed: %w", loginErr)
	}

	// Account requires 2FA
	if f.opt.TOTPSecret == "" {
		return nil, errors.New("account requires 2FA but no totp_secret configured")
	}

	// Try to reveal (decrypt) TOTP secret; fall back to plaintext if not obscured
	totpSecret, err := obscure.Reveal(f.opt.TOTPSecret)
	if err != nil {
		fs.Debugf(f, "TOTP secret is not obscured, using as plaintext")
		totpSecret = f.opt.TOTPSecret
	}

	// Try TOTP with T, T-1, T+1 time windows to handle clock skew
	timeOffsets := []int64{0, -1, 1}
	var lastErr error

	for i, offset := range timeOffsets {
		var code string
		if offset == 0 {
			code, err = generateTOTP(totpSecret)
		} else {
			code, err = generateTOTPWithOffset(totpSecret, offset)
		}
		if err != nil {
			return nil, fmt.Errorf("failed to generate TOTP code: %w", err)
		}

		if offset != 0 {
			fs.Debugf(f, "Generated TOTP code for 2FA with time offset %d (attempt %d/3)", offset, i+1)
		} else {
			fs.Debugf(f, "Generated TOTP code for 2FA (attempt 1/3)")
		}

		resp, loginErr = internxtauth.DoLogin(ctx, cfg, f.opt.Email, password, code)
		if loginErr != nil {
			var httpErr *sdkerrors.HTTPError
			if errors.As(loginErr, &httpErr) && (httpErr.StatusCode() == 401 || httpErr.StatusCode() == 403) {
				lastErr = loginErr
				fs.Debugf(f, "2FA failed with time offset %d, trying next window", offset)
				continue
			}
			return nil, fmt.Errorf("re-login failed: %w", loginErr)
		}

		fs.Debugf(f, "2FA succeeded with time offset %d", offset)
		return resp, nil
	}

	return nil, fmt.Errorf("re-login failed (all TOTP time windows failed): %w", lastErr)
}

// refreshOrReLogin tries to refresh the JWT token first; if that fails (usually with 401),
// it falls back to a full re-login using stored credentials.
func (f *Fs) refreshOrReLogin(ctx context.Context) error {
	refreshErr := f.refreshJWTToken(ctx)
	if refreshErr == nil {
		f.cfg.BasicAuthHeader = computeBasicAuthHeader(f.bridgeUser, f.userID)
		fs.Debugf(f, "Token refresh succeeded")
		return nil
	}

	fs.Debugf(f, "Token refresh failed (%v), attempting re-login with stored credentials", refreshErr)

	resp, err := f.reLogin(ctx)
	if err != nil {
		return fmt.Errorf("re-login fallback failed: %w (original refresh error: %v)", err, refreshErr)
	}

	useToken := resp.NewToken
	if useToken == "" {
		useToken = resp.Token
	}

	oauthToken, err := jwtToOAuth2Token(useToken)
	if err != nil {
		return fmt.Errorf("failed to parse re-login token: %w", err)
	}
	err = oauthutil.PutToken(f.name, f.m, oauthToken, true)
	if err != nil {
		return fmt.Errorf("failed to save re-login token: %w", err)
	}

	f.cfg.Token = oauthToken.AccessToken
	f.bridgeUser = resp.User.BridgeUser
	f.userID = resp.User.UserID
	f.cfg.BasicAuthHeader = computeBasicAuthHeader(f.bridgeUser, f.userID)
	f.cfg.Bucket = resp.User.Bucket
	f.cfg.RootFolderID = resp.User.RootFolderID

	fs.Debugf(f, "Re-login succeeded, new token expiry: %v", oauthToken.Expiry)
	return nil
}

// getBackoffDuration returns the backoff duration for a given attempt number with jitter.
// Backoff steps: 1m, 5m, 15m, 1h, 1h (maxed at 1h after attempt 5)
// Adds ±10% random jitter.
func getBackoffDuration(attempt int) time.Duration {
	var baseDuration time.Duration
	switch {
	case attempt == 1:
		baseDuration = time.Minute
	case attempt == 2:
		baseDuration = 5 * time.Minute
	case attempt == 3:
		baseDuration = 15 * time.Minute
	case attempt >= 4:
		baseDuration = time.Hour
	default:
		baseDuration = time.Minute
	}

	// Add ±10% jitter
	jitter := float64(time.Duration(mrand.Int63n(int64(baseDuration) / 10)))
	return baseDuration - time.Duration(jitter)
}

// reAuthorize is called after getting 401 from the server.
// It serializes re-auth attempts and uses a soft circuit-breaker with exponential backoff.
func (f *Fs) reAuthorize(ctx context.Context) error {
	f.authMu.Lock()
	defer f.authMu.Unlock()

	// Check if circuit breaker is open
	if !time.Now().After(f.nextAuthAllowed) {
		return fmt.Errorf("re-authorization blocked until %v (attempt %d/5)", f.nextAuthAllowed, f.authFailCount)
	}

	// Check if we've exceeded max retries
	if f.authFailCount >= 5 {
		return errors.New("auth exceeded max retries: manual re-auth required")
	}

	err := f.refreshOrReLogin(ctx)
	if err != nil {
		// Increment failure count and set backoff
		f.authFailCount++
		backoff := getBackoffDuration(f.authFailCount)
		f.nextAuthAllowed = time.Now().Add(backoff)
		fs.Debugf(f, "Re-authorization failed (attempt %d/5), backing off %v until %v", f.authFailCount, backoff, f.nextAuthAllowed)

		// Check if this was the 5th failure
		if f.authFailCount >= 5 {
			return errors.New("auth exceeded max retries: manual re-auth required")
		}
		return err
	}

	// Success - reset failure count
	f.authFailCount = 0
	f.nextAuthAllowed = time.Time{}
	fs.Debugf(f, "Re-authorization succeeded, failure count reset to 0")
	return nil
}

// generateTOTP generates a Time-based One-Time Password (TOTP)
// using the given secret (base32 encoded), current time, and defaults (30s period, 6 digits).
// It implements RFC 6238.
func generateTOTP(secret string) (string, error) {
	return generateTOTPWithOffset(secret, 0)
}

// generateTOTPWithOffset generates a TOTP code for a specific time offset.
// offset: number of 30-second periods offset from current time (e.g., -1 = 30 seconds ago, +1 = 30 seconds ahead)
func generateTOTPWithOffset(secret string, offset int64) (string, error) {
	// Clean up input
	secret = strings.TrimSpace(strings.ToUpper(secret))

	// Decode base32 secret
	// Try standard encoding first, then with no padding
	key, err := base32.StdEncoding.DecodeString(secret)
	if err != nil {
		// Try adding padding if needed or using NoPadding
		key, err = base32.StdEncoding.WithPadding(base32.NoPadding).DecodeString(secret)
		if err != nil {
			return "", fmt.Errorf("invalid base32 secret: %v", err)
		}
	}

	// Calculate time step with offset
	period := 30
	t := (time.Now().Unix() / int64(period)) + offset

	// Pack time step into 8 bytes (big endian)
	buf := make([]byte, 8)
	binary.BigEndian.PutUint64(buf, uint64(t))

	// HMAC-SHA1
	mac := hmac.New(sha1.New, key)
	mac.Write(buf)
	sum := mac.Sum(nil)

	// Dynamic truncation
	truncationOffset := sum[len(sum)-1] & 0xf
	binCode := binary.BigEndian.Uint32(sum[truncationOffset : truncationOffset+4])

	// Remove most significant bit
	binCode &= 0x7fffffff

	// Modulo 10^6
	mod := int(math.Pow10(6))
	otp := int(binCode) % mod

	// Format with 6 digits zero padded
	return fmt.Sprintf("%06d", otp), nil
}
