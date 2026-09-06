# ADR-001: Encryption of cloud credentials at rest

Status: PROPOSED — design gate for remediation item RC-3 (High security).
Target: implement before the next feature release.

## Context & problem

CloudBridge stores rcloud-provider credentials (and similar secrets) on-device.
Today these are only *obscured* by rclone's reversible `obscure` encoding, not
encrypted. `Rclone.java` writes `rclone.conf` with obscured passwords; the
Internxt reauth flow keeps a refresh token (`InternxtReauth.kt`). Anyone with
file-system access to the app's private storage (rooted device, forensic
extraction, ADB backup on a non-protected profile) can recover every password,
because rclone's `obscure` is a fixed, key-less reversible transform.

Phase 1 already set `android:allowBackup="false"` so `rclone.conf` is no longer
extractable via ADB/Google backup. This ADR defines the remaining at-rest
protection.

## Secret inventory

| Location | Contents | Current protection |
|---|---|---|
| `<app>/files/rclone.conf` | provider passwords, OAuth tokens, S3 keys, WebDAV creds, crypt passwords | rclone `obscure` (reversible) |
| SharedPreferences (default + custom) | Internxt refresh token, user prefs (non-secret) | plaintext |
| Update cache (`externalCacheDir`) | downloaded update APKs | none (transient) |
| Logs (`getExternalFilesDir("logs")`) | rclone stderr, may echo secrets if verbose | plaintext |
| Exported/imported config | `rclone.conf` copies shared by the user | obscured, user-controlled |

Decision scope: only the first two rows require at-rest encryption. Logs and
update cache must never contain secrets (enforced by guardrails below).

## Decision

Adopt **Android Keystore-backed encryption** for the values that rclone cannot
protect itself, while keeping rclone's `obscure` for values that rclone must
read natively (so we do not break rclone compatibility).

- **rclone.conf**: keep rclone's own `obscure` encoding for values rclone parses
  at runtime (rclone cannot read Keystore-encrypted values). Instead, protect
  the **whole file** by placing it under Android's `EncryptedFile` (Jetpack
  Security / Tink) using an AES-256 key stored in the Android Keystore
  (hardware-backed where available). rclone reads via our shim that decrypts to
  a transient stream/pipe, never writing plaintext to disk.
- **SharedPreferences secrets** (Internxt refresh token, future tokens): migrate
  to `EncryptedSharedPreferences` (Jetpack Security), master key in Keystore.
- **Key alias**: `cb_master_key`, created on first run, `setUserAuthenticationRequired(false)`
  (do NOT make biometric mandatory — see Failure modes).
- **Library**: prefer Jetpack Security (`androidx.security:security-crypto`) over
  a hand-rolled Keystore wrapper, to reduce crypto-misuse risk.

Rejected: full custom AES with a hardcoded key (insecure), and requiring
biometric unlock (breaks background sync / Tasker triggers).

## Failure modes & recovery

- **New device / restore**: `allowBackup=false` means rclone.conf is NOT
  restored. User must re-import config or re-authenticate. Acceptable and safer.
- **App reinstall**: Keystore key is wiped on uninstall (app-authenticated key).
  A reinstalled app starts fresh; old encrypted blobs are unreadable but the
  app re-creates state on re-auth. Acceptable.
- **Lost hardware-backed key** (rare, e.g. factory reset of keystore): app
  detects decryption failure, clears corrupt config, prompts re-auth. No silent
  data loss in the cloud (creds are only a cache; user re-enters them).
- **Config import/export**: export writes an obscured (rclone-native) file the
  user explicitly chooses; import decrypts-transparently on first use. Never
  write the Keystore key outside the device.
- **Multi-profile**: one Keystore key per app install is sufficient; no shared
  cross-profile secret.

## Migration plan

1. Add `androidx.security:security-crypto` dependency.
2. Introduce a `CredentialStore` wrapper: on first run, generate the Keystore
   master key and migrate any plaintext SharedPreferences secrets to
   `EncryptedSharedPreferences`.
3. Move `rclone.conf` access behind an `EncryptedFile`-backed read/write path;
   keep the on-disk filename, change the read path so rclone is fed the
   decrypted content via the existing pipe/stdin integration.
4. Keep `obscure` inside rclone.conf (defense in depth).
5. Add a one-time migration unit test: plaintext config in → encrypted out,
   readable by the app, unreadable as raw bytes.

## Immediate guardrails (already landed)

- Phase 1: `android:allowBackup="false"` — blocks backup exfil of rclone.conf.
- No new code path may store tokens in plaintext SharedPreferences; new token
  storage must use `CredentialStore` once introduced.
- Logs must not print credential values; `FLog` calls must not interpolate
  passwords/tokens (verify during code review).

## Open questions (resolve before implementation)

- Does rclone accept its config via stdin/pipe on the Android build, or only via
  `--config <file>`? If only file-based, the EncryptedFile shim must decrypt to
  a short-lived temp file with `0600` perms in the private dir, deleted after
  rclone reads it. Confirm against the librclone integration.
- Decide whether to gate the master key behind `setUserAuthenticationRequired`
  for an optional "require unlock to open app" preference (off by default).
