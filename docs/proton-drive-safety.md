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
