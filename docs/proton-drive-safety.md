# Proton Drive safety notes

This fork treats Proton Drive as an experimental backend and favours preserving data over aggressive automatic recovery.

## Engine fixes carried by Rareities/rclone

The pinned rclone engine includes:
- decrypted-name fallback for objects created by newer Proton Drive clients;
- per-Fs credential handlers so one Proton remote cannot write refreshed credentials into another remote's config;
- preservation of cached credentials when reusable-login initialization fails transiently;
- corrected source/destination directory-cache invalidation after Proton server-side moves;
- general rclone transfer/cancellation safety patches carried by the engine fork.

## App-side safeguards

Round Sync:
- disables Proton backend metadata caching for operations, because the backend cache does not observe changes from other Proton clients;
- serializes sync workers to reduce refresh-token races between overlapping rclone processes;
- treats non-zero rclone exits as failed tasks.

The app deliberately does **not** automatically purge folders, empty Proton trash, recreate files, or force destructive bisync recovery after Proton API anomalies.

## Unresolved upstream cases

Until upstream fixes land, errors such as these must stop the run and preserve local data:
- refresh-token race / invalid refresh token;
- crypto-refresh validation failures such as Code 200501;
- incomplete or zombie draft conflicts (for example Code 2500 followed by 2501);
- other Proton API/backend inconsistencies.

Do not automatically convert those errors into remote deletion or resync.


## Runtime Proton safeguards

Normal Proton Drive operations use one shared WorkManager queue for scheduled syncs and manual uploads/downloads/moves/deletes. A process-local fair lock is also held while a Proton worker owns the backend. This is intentionally conservative until the upstream refresh-token adoption changes are released.

### Refresh-token/session conflicts

The current Round Sync task model does not generate `--backup-dir`, `--compare-dest`, or `--copy-dest`, so it avoids the known single-command multi-Fs trigger. If Proton returns `Code=10013` / "Invalid refresh token", the app:
- stops automatic retries;
- preserves the cached session;
- suppresses failure follow-up tasks;
- tells the user to wait for other Proton/rclone activity and retry manually.

### Interrupted uploads / orphaned drafts

If one run contains the Proton conflict/not-found pattern (`Code=2500` plus `Code=2501`), the app classifies it as an orphaned/incomplete draft and:
- stops retries;
- never purges the parent directory;
- never runs Proton cleanup automatically;
- never converts the error into a forced resync;
- preserves the local file and surfaces recovery guidance.

### Network and rate limits

Known transient network failures (timeouts, DNS/connectivity failures, 500/502 responses) can request at most two WorkManager retries.

Proton rate-limit responses (`429` / `Code=2011`) are never automatically retried. Repeated authentication attempts can extend the rate limit, so the operation stops and keeps the stored credentials intact.
