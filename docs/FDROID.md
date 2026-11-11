# F-Droid publishing progress and procedure

## Status — 11 November 2026

CloudBridge **1.0.5** has been submitted to official F-Droid. The app is **not
listed yet**: inclusion review, acceptance and repository publication remain
pending. The merge-request pipeline passed. A maintainer closed the request on
8 November because its description did not follow the required template. The
description and title were corrected, and linsui reopened the merge request
on 9 November. Their open review thread asks for the build preparation helper
to live in CloudBridge itself. The v1.0.5 recipe and its passing pipeline remain
the live submission; the local v1.0.6 candidate addresses the request.

Local v1.0.6 validation: preparation passed with Go 1.26.0 and the exact
pinned rclone checkout, with the same production inputs before and after
cleanup on all four ABIs. The offline unsigned OSS release build and release
lint passed. The universal and four split APKs have the expected package,
version and version codes (32710 and 32716–32719). The signed reference
comparison and independent F-Droid CI still need the published release.

- [Packaging merge request !56366](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/56366)
- [Original packaging request #4754](https://gitlab.com/fdroid/rfp/-/work_items/4754)
- [Passed pre-submission pipeline: all nine jobs](https://gitlab.com/thies.schuelken/fdroiddata/-/pipelines/3041762458)
- [Merge-request pipeline](https://gitlab.com/thies.schuelken/fdroiddata/-/pipelines/3042051820)
- [Published v1.0.5 release](https://github.com/thies2005/CloudBridge/releases/tag/v1.0.5)

The submitted application source is
`ca270e3b54911291f1d8c75ef6343e53834b96b7`, with universal APK/AAB version code
**32650** and application ID `de.schuelken.cloudbridge`.

## What has been completed

The source scan, full unsigned Android build, APK scanner and signed-APK
verification passed. Local comparison also passed using both
`apksigcopier compare --unsigned` and F-Droid's own APK comparator. Published
APK/AAB identities, signatures and release checksums were verified.

The Android build uses the pinned rclone fork with `noselfupdate,nogui`, omitting
the unused embedded browser GUI. The packaging helper checks all four ABI
dependency graphs and protects production compiler/embedded inputs before
removing explicitly listed unused dependency fixtures. Narrow scanner
exceptions cover tracked test fixtures, the uncompiled GUI archive and two
non-executable publicsuffix lookup tables. Automated checks do not replace
F-Droid's license and inclusion review.

The existing developer signing certificate is retained:

```text
a352eceb86e3d2fdd6a68ff68ab0637e978b843c8d885fa79e87e16b0cd06993
```

`Binaries` and `AllowedAPKSigningKeys` require the original developer-signed
APK to match the source rebuild. F-Droid receives no private signing key.

## Template correction and remaining review items

The original description used custom build/validation sections and omitted the
required new-app checklist. Successful CI did not satisfy that submission
requirement. The correction uses the [official new-app template](https://gitlab.com/fdroid/fdroiddata/-/blob/master/.gitlab/merge_request_templates/Default.md),
retains all 28 items in order, embeds release screenshots and links dated
CloudBridge development history. The exact corrected description is saved in
[FDROID_MR.md](FDROID_MR.md).

All 19 Reports-tab entries are explained: three minor permission notices and
16 informational entries covering permissions, reproducible APK, optimizer,
ABI/size, certificate and localized Fastlane metadata. The recipe and published
release were not changed for this description-only correction.

Three boxes remain unchecked rather than claiming unsupported compliance:
full inclusion-policy compliance, external rclone fetched rather than a Git
submodule, and universal-only F-Droid distribution rather than separate ABI
entries. The manual-update exception is also explained. These remain review
questions; correcting the template does not establish acceptance.

The posted description checks original-author notification, but the linked
evidence only establishes consent from the maintainer of the CloudBridge fork.
Leave that item unchecked in the follow-up unless there is a verifiable reply
from the relevant original author. Preserve the fork history and attribution.

For every future new-app submission, retrieve the current template, retain all
required headings/items, verify each assertion, explain exceptions and inspect
the Reports tab even when CI passes. Never tick every box automatically.

## Continue the current submission

1. Review [the upstream helper](../scripts/fdroid_prepare.py) and the
   [follow-up recipe](../fdroid/metadata/de.schuelken.cloudbridge.yml).
   Its `prebuild` now calls `bash scripts/prepare-fdroid.sh --prune-fixtures`.
   Use a fresh disposable checkout: pruning removes explicitly listed unused
   fixture/binding paths and redundant module ZIPs from its Go cache. The
   ordinary preparation command without this flag does not prune anything.
2. Publish the new stable release with the existing signing identity once
   authorized. The proposed code **32710** assumes one release commit on the
   current base; recalculate it from the final full Git history if that changes.
3. On the fork's existing `de.schuelken.cloudbridge` branch, replace the single
   v1.0.5 entry with the tested v1.0.6 entry. Insert the full published SHA,
   remove `disable` and delete `metadata/de.schuelken.cloudbridge/fdroid-prepare.patch`.
   The new source already omits the three legacy images; the recipe needs no
   `rm` entries for them. Retain both signing gates and rerun lint, build, source
   scan, APK scan and reproducibility verification on that branch.
4. Update the existing MR description using the current template. Replace
   release links and CI evidence, explain any new Reports entries and correct
   the original-author checkbox. Keep remaining policy, rclone submodule, ABI
   and manual-update exceptions explicit. Reply in the existing helper thread
   with the published source link and passing pipeline when available.
5. Wait for F-Droid maintainers to accept and merge the request, complete their
   independent build/verification and publish the repository entry. Add an
   installation badge only after the listing is available.

After the new pipeline passes, the reply to the review thread can be short:
“The preparation helper now lives in CloudBridge at [published source link].
The recipe invokes it directly and no longer carries `fdroid-prepare.patch`.
The [new pipeline] passed the source scan, build and signed APK comparison.”

## Prepare a later release

1. Follow the [shared release and signing guide](PUBLISHING.md): bump version
   name/code, add release notes and the English Fastlane changelog, run required
   checks and publish an immutable signed GitHub release.
2. Update the fdroiddata build entry's version, code and full source commit SHA
   together. Review toolchain pins and fixture handling against the new rclone
   dependencies. Updates are currently manual (`AutoUpdateMode: None`).
3. In the F-Droid environment, run `bash scripts/prepare-fdroid.sh --prune-fixtures` before scanning
   and build with `unsignedRelease=true` and `rcloneOffline=true`. Keep ordinary
   ABI splits enabled and select the universal unsigned APK.
4. Run the metadata checks and full build:

   ```sh
   fdroid readmeta
   fdroid rewritemeta de.schuelken.cloudbridge
   fdroid lint de.schuelken.cloudbridge
   fdroid build --verbose de.schuelken.cloudbridge
   ```

5. Require a successful comparison with the signed APK from that exact release
   and a passing hosted pipeline, then submit the fdroiddata update for review.
   Use the actual F-Droid CI formatter environment: different YAML library
   versions can produce formatting-only failures.

## Download failures encountered during submission

GitHub temporarily returned HTTP 503/504 for Gradle and the signed reference
APK. These failures happened during downloads; the Android compilation itself
succeeded. The Gradle recipe now fetches the official asset through GitHub's
API, verifies the original pinned checksum and populates the standard caches.
Both original Gradle launchers retain their independent verification.

A separate download-only CI diagnostic subsequently passed all four tested
routes, including the exact canonical APK URL and F-Droid downloader. Retrying
the full job then passed. The diagnostic CI configuration lives on an isolated
branch and is not included in the packaging merge request.

For a similar failure, inspect the first error above the artifact-upload logs.
`No files to upload` can be a secondary consequence of an earlier failure.
Confirm download availability before retrying the failed job; a retry creates
a new job URL while the old job remains marked failed. Never remove the signing
pin or APK comparison to work around a network error.
