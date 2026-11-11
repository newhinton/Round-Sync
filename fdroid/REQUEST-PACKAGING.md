# CloudBridge 1.0.3 — F-Droid packaging request

The initial request already exists at https://gitlab.com/fdroid/rfp/-/work_items/4754.
Do not open a duplicate. This file records the original v1.0.3 submission details.
For the prepared v1.0.4 fixes and another review, follow [REVIEW-FOLLOWUP.md](REVIEW-FOLLOWUP.md).

**Suggested title:** CloudBridge — cloud file manager powered by rclone

## App details

- Name: CloudBridge
- Application ID: `de.schuelken.cloudbridge`
- Source: https://github.com/thies2005/CloudBridge
- Release: https://github.com/thies2005/CloudBridge/releases/tag/v1.0.3
- Source tag: `v1.0.3` (use its resolved full commit SHA in the build recipe)
- License: GPL-3.0-only
- Website: https://thies2005.github.io/CloudBridge/
- Issue tracker: https://github.com/thies2005/CloudBridge/issues
- Author: thies2005; I am requesting inclusion of my own maintained fork.

## Description and fork improvements

CloudBridge is a free Android cloud file manager powered by rclone. It supports
file transfers, scheduled sync/copy tasks, encryption remotes, media streaming
and Android Storage Access Framework integration. It is a maintained fork of
Round-Sync/RCX/rcloneExplorer with Internxt login/token-renewal improvements,
Drime support, updated rclone, task/reporting fixes and Android compatibility
improvements. Store descriptions, screenshots, icons and changelogs are in
`fastlane/metadata/android/`.

## Signing requirement

Please use **developer-signed reproducible distribution from the first
F-Droid version**, retaining the same certificate as Google Play and GitHub.
I do not want a separate F-Droid-signed variant with an incompatible key.

- Reference APK: https://github.com/thies2005/CloudBridge/releases/download/v1.0.3/cloudbridge_v1.0.3-oss-universal-release.apk
- Version name: `1.0.3`
- Universal APK version code: `32610`
- Expected certificate SHA-256:
  `a352eceb86e3d2fdd6a68ff68ab0637e978b843c8d885fa79e87e16b0cd06993`
- Use `Binaries` and `AllowedAPKSigningKeys` in the recipe; do not remove them
  to bypass a failed reproducibility check. No private signing key is supplied.

## Build preparation

- Draft recipe: https://github.com/thies2005/CloudBridge/blob/v1.0.3/fdroid/metadata/de.schuelken.cloudbridge.yml
- Build/signing guide: https://github.com/thies2005/CloudBridge/blob/v1.0.3/docs/PUBLISHING.md
- Flavor: `oss`; JDK 17; Go 1.26.0; Android SDK platform 36; NDK 29.0.14206865.
- The rclone fork is public and pinned to a full source SHA in
  `gradle.properties`. `bash scripts/prepare-fdroid.sh` fetches source/modules;
  `./gradlew :app:assembleOssRelease -PunsignedRelease=true -PrcloneOffline=true
  --no-daemon` rebuilds without signing credentials and without Go downloads.
- Keep ordinary ABI splits enabled and select the universal unsigned output.

The recipe is intentionally disabled pending F-Droid's toolchain provisioning,
source/dependency scan and independent server build/reproducibility verification.
Its upstream URL and certificate pin identify the intended distribution; they
do not by themselves prove reproducibility. Please review Go module archives
and dependency fixtures rather than using blanket scanner exclusions. The
offline source verifier requires Git metadata; rebuilding a source tarball alone
needs the tagged app checkout and a freshly prepared pinned rclone checkout.

I can help resolve packaging and reproducibility issues and maintain future
release metadata.
