<!-- Based on fdroiddata Default.md, retrieved 8 November 2026. -->



## Abstract for new app inclusion

### Please introduce the functions and usage of your app in summary, and also attach some screenshots.

CloudBridge is an Android cloud file manager powered by rclone. Users can browse local/cloud files, upload/download/move files, schedule sync/copy tasks, configure encrypted remotes, stream media and access SD/USB storage through Android SAF. Application ID: `de.schuelken.cloudbridge`.

Screenshots from the immutable submitted release:

![Storage connections](https://raw.githubusercontent.com/thies2005/CloudBridge/v1.0.5/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png)
![Cloud folders](https://raw.githubusercontent.com/thies2005/CloudBridge/v1.0.5/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png)


### Please elaborate why you wrote this app. Please highlight the unique functions it provides over other similar apps in F-Droid.

This fork continues Round-Sync/RCX/rcloneExplorer with provider integration and maintenance work: Internxt automatic token renewal, Drime support, updated rclone and Android compatibility, plus task/reporting fixes. These are the concrete reasons for offering this fork alongside its predecessors; no claim is made that every feature is absent from all other F-Droid apps. The distinct app name, icon and application ID identify CloudBridge separately, and upstream attribution is retained.

Development history predates the three-month policy threshold: [provider support documentation, 3 February 2026](https://github.com/thies2005/CloudBridge/commit/85be4a9d), [CloudBridge rebranding, 23 April 2026](https://github.com/thies2005/CloudBridge/commit/1b1e874a), and subsequent maintenance through the submitted November release. This is not a newly created rebranding-only submission.


## Checklist

### Policy

* [ ] The app complies with the [inclusion policy](https://f-droid.org/docs/Inclusion_Policy). _Pay special attention to Quality point 6._
* [x] The original app author has been notified (and does not oppose the inclusion). If you are not the author, please paste the link of the reply from the author.
* [x] The upstream app source code repo contains the app metadata in a [Fastlane](https://gitlab.com/snippets/1895688) or [Triple-T](https://gitlab.com/snippets/1901490) folder structure. The summary and description must be included and images, icon, and changelog should also be provided for better user experience. The `en-US` locale must be included.

### Docs

* [x] Please read [the guide](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md) first if this is your first contribution.
* [x] Please make sure your metadata follows the best practice in [our templates](https://gitlab.com/fdroid/fdroiddata/tree/master/templates).
* [x] Please read the [Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/) and make sure your metadata is valid.
* [x] Please read the [Quick Start Guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/).

### Merge Request Setup

* [x] The title of this merge request should follow "New app: app name" format.
* [x] Please make sure your fdroiddata fork is public and your branch is not protected. See <https://docs.gitlab.com/user/project/repository/branches/protected/>.
* [x] Please read [our Git guide](https://gitlab.com/fdroid/wiki/-/wikis/Tips-for-fdroiddata-contributors/Git-Usage) if you don't know how to rebase your branch. Don't rebase your branch if there is no conflict.
* [x] All related [fdroiddata](https://gitlab.com/fdroid/fdroiddata/issues) and [RFP issues](https://gitlab.com/fdroid/rfp/issues) have been referenced in this merge request
* [x] Please only submit one app in one MR.

### Metadata

* [x] Metadata must be put in `metadata/<applicationId>.yml`.
* [x] Metadata must be a valid YAML file.
* [x] Metadata must use LF as line ending.
* [x] Don't add summary/description/changelog/images or anything that should be provided in upstream repo. Please check the Changes tab to make sure there is no other unrelated files added in the MR.
* [x] Releases are tagged and auto update is enabled unless there is a special reason.
* [x] There is an issue tracker and contact info of the author so that we can report bugs and contact the author.
* [x] An AuthorName must be added. It doesn't need to be the real name.
* [ ] External repos are added as git submodules instead of srclibs. You can update git submodules without opening an MR in this repo and the submodule is covered by our scanner.
* [x] Enable [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds). We'll use your signature for improved security/reliability, also allowing users to switch between different channels. Do note that if you don't enable reproducible build then the apk will be signed with our key so you can't enable it later. If you can't enable this, please add the reasons here.
* [ ] Setup abi split if the APK is large and the splitted ones can be much smaller.
* [x] Only the latest versions should be kept in the metadata before it's merged. If you update the metadata, please replace the old versions with the new ones.
* [x] Don't add any disabled versions in the metadata.
* [x] The `commit` field should be the full hash. Please don't use tag or branch in commit.

### Pipeline

* [x] All pipelines should pass.
* [x] All warnings and errors in the Reports tab should be fixed or explained.
* [x] F-Droid CI runners are under GitLab's FOSS program, so there's no need for you to pay for any CI time. If Gitlab starts asking for phone numbers or credit cards don't submit anything, just leave a note in the MR so we know we need to trigger the CI.

## Checklist evidence and exceptions requiring review

- **Policy (left unchecked):** GPL-3.0-only source, OSS flavor, separate fork identity and maintenance history are documented upstream. The maintainer consents in [rfp#4754](https://gitlab.com/fdroid/rfp/-/work_items/4754). [ASSETS.md](https://github.com/thies2005/CloudBridge/blob/v1.0.5/ASSETS.md) records current AI-generated maintainer artwork and historical attribution. Three unused legacy documentation images with unresolved exact license URLs are removed by the recipe. Passing scanners are not a complete license, trademark, dependency or inclusion audit; final policy compliance remains for review. Quality point 6 is addressed by the dated development history above, not by assuming inherited history alone is enough.
- **Author consent:** The submitting account is the maintainer of the CloudBridge fork and requests inclusion. This does not claim that the Round-Sync/RCX/rcloneExplorer authors separately endorsed CloudBridge. Attribution and their license notices remain intact.
- **Upstream metadata:** [English Fastlane directory](https://github.com/thies2005/CloudBridge/tree/v1.0.5/fastlane/metadata/android/en-US) contains short/full descriptions, icon, screenshots and `changelogs/32650.txt`.
- **Docs and setup:** CONTRIBUTING.md, the general/Gradle metadata templates, Build Metadata Reference, Quick Start Guide and linked Git guide were read for this correction. The fork is publicly readable and branch `de.schuelken.cloudbridge` is unprotected. This MR adds only `metadata/de.schuelken.cloudbridge.yml` and its build helper patch; no store listing content or CI override is added. Only the latest version, 1.0.5, is present; it is enabled and pinned to the full source SHA. The GitHub issue tracker is the public contact channel; AuthorName is `thies2005`.
- **Tagged release/manual-update exception:** [v1.0.5](https://github.com/thies2005/CloudBridge/releases/tag/v1.0.5) is tagged and immutable. `AutoUpdateMode: None` / `UpdateCheckMode: None` are intentional during initial review: the pinned rclone/Go dependency fixture list, source scanner exceptions and reproducible toolchain need validation for dependency changes. New versions will be submitted manually with tested recipes. Please advise whether tag-based updates should be enabled before acceptance.
- **External-repository exception (left unchecked):** rclone is currently fetched by the application's existing preparation flow, not represented by a Git submodule or srclib. It is pinned to `9f9d067ea927bada5d563f5a263ae75b41a1508c`; the offline build verifies its clean Git SHA and extracted dependencies remain visible to the scanner. Converting the upstream source layout to a submodule would require a new app release and another reproducibility check. This exception is disclosed for maintainer review, not asserted to be accepted.
- **ABI split exception (left unchecked):** Upstream publishes per-ABI APKs with codes 32656/32657/32658/32659 and a universal APK with code 32650. Ordinary split generation stays enabled, but this initial recipe distributes only the universal APK, which is the independently verified reference. Per-ABI distribution would require separate build entries, matching binary URLs/codes and verification of every output; please advise if required before inclusion.
- **Reproducibility:** `Binaries` and `AllowedAPKSigningKeys` are enabled from the first F-Droid version. The developer certificate SHA-256 is `a352eceb86e3d2fdd6a68ff68ab0637e978b843c8d885fa79e87e16b0cd06993`. No private key is provided. Please retain both gates when resolving failures.

## Reports tab: all 19 findings explained

The [MR code-quality report](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/56366/reports/code-quality) contains **3 minor permission notices and 16 informational entries**, not 19 build failures:

- `WRITE_EXTERNAL_STORAGE` supports legacy Android file access (manifest limits it to API 29); `MANAGE_EXTERNAL_STORAGE` supports the file-manager's direct local storage access on newer Android; `INTERNET` is needed for cloud transfers. These are the three minor notices. The app exposes Android's all-files-access settings for the user grant; SAF access is also supported.
- `ACCESS_NETWORK_STATE` supports connectivity checks and `ACCESS_WIFI_STATE` supports the transfer Wi-Fi lock; `POST_NOTIFICATIONS` supports task notifications; `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` and `WAKE_LOCK` support ongoing transfers; `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM` and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` support scheduling/background execution. The generated `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` protects AndroidX dynamic receivers. These ten entries describe declared permissions, not additional scanner failures.
- The remaining six informational entries record the reproducible reference APK, R8 marker, universal ABI/size (110 MB), signing-certificate identity, and the en-US/de-DE Fastlane content. The large universal size is why per-ABI packaging is explicitly raised above. The certificate matches the pinned developer fingerprint; the R8 marker records the release optimizer configuration.

## Build and validation evidence

Targets v1.0.5, app source `ca270e3b54911291f1d8c75ef6343e53834b96b7`, universal version code **32650**. The Android rclone tags `noselfupdate,nogui` exclude the unused embedded browser GUI from all four ABI dependency graphs.

Go 1.26.0 is checksum-verified outside the source tree; JDK 17 is selected explicitly and NDK 29.0.14206865 is pinned. Preparation precedes scanning. The helper protects actual compiler/embedded inputs before pruning 64 explicitly listed unused dependency fixtures/foreign-binding targets. Narrow exceptions cover tracked test fixtures, the uncompiled GUI archive and two non-executable publicsuffix tables; there is no blanket module-cache exclusion. Git metadata is needed for the embedded app revision and clean rclone-SHA checks.

The Gradle 8.13 official asset API download addresses transient GitHub 504s; the original distribution checksum and both launcher verification paths are retained. A separate download diagnostic was not included in this MR.

- [Pre-submission pipeline 3041762458](https://gitlab.com/thies.schuelken/fdroiddata/-/pipelines/3041762458): all nine jobs passed.
- [MR pipeline 3042051820](https://gitlab.com/thies.schuelken/fdroiddata/-/pipelines/3042051820): passed for source branch commit `0ee26dea2df71dce269575c2c786857a75950d48`.
- Full unsigned native/Android build and source scan passed in the official buildserver-trixie environment, with zero source-scan errors. APK scan found no flagged non-free classes.
- Both `apksigcopier compare --unsigned` and `fdroidserver.common.verify_apks` passed against the published signed universal APK. Release checksums, APK/AAB identities and signatures were verified against the existing developer certificate.
- Metadata read/lint/schema/format checks passed. CI's formatter environment was used and repeated rewrites were byte-identical.
- Remaining source warnings: an HTTP gzip test fixture and public golang-jwt v4/v5 HMAC test fixture keys. They are test data, not application credentials. These explanations do not replace manual license/inclusion review.

The earlier description omitted the required template. This corrected description retains every checklist item and explicitly identifies outstanding exceptions rather than checking unsupported claims. No app bytes or build recipe were changed for this correction.

---------------------

<!--Add the corresponding issue number or remove this if this merge request does not close an issue at rfp.-->
Closes fdroid/rfp#4754

/label ~"New App"
