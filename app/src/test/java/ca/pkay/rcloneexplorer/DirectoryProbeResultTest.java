package ca.pkay.rcloneexplorer;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DirectoryProbeResultTest {
    @Test
    public void recoverableProbeFailuresDoNotAskTheUserToSignIn() {
        String[] diagnostics = {
            "re-authorization blocked until tomorrow (attempt 1/5)",
            "auth exceeded max retries: manual re-auth required",
            "failed to fetch user info (re-auth failed: dial tcp: i/o timeout): status 401",
            "re-login fallback failed: login: rate limited (status 429)",
            "failed to fetch user info (re-auth failed: re-login fallback failed: re-login check failed: timeout): refresh: status 401",
            "couldn't list files: status 403"
        };
        for (String message : diagnostics) {
            assertFalse(message, new Rclone.DirectoryProbeResult(1, message)
                    .requiresInternxtReauthentication());
        }
    }

    @Test
    public void confirmedReLoginRejectionOrMissingCredentialsAsksForSignIn() {
        assertTrue(new Rclone.DirectoryProbeResult(1,
                "re-login fallback failed: re-login check failed: rejected credentials (status 401)")
                .requiresInternxtReauthentication());
        String missingSecret = "account requires 2FA but no totp_secret configured";
        assertTrue(new Rclone.DirectoryProbeResult(1, missingSecret).requiresInternxtReauthentication());
        assertFalse(new Rclone.DirectoryProbeResult(0, missingSecret).requiresInternxtReauthentication());
    }
}
