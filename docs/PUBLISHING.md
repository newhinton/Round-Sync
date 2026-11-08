# Publishing CloudBridge on Play, GitHub and F-Droid

Use the `oss` release flavor and application ID `de.schuelken.cloudbridge`
everywhere. Obtainium follows the GitHub releases; it does not require a separate
app upload or developer registration.

## 1. Confirm the shared signing identity

The existing GitHub release APKs use this public certificate SHA-256:

```text
a352eceb86e3d2fdd6a68ff68ab0637e978b843c8d885fa79e87e16b0cd06993
```

In **Play Console → CloudBridge → Play app signing**, compare the **app signing
key certificate** SHA-256 with that fingerprint. Depending on the Console
layout, the page is under **App integrity** or **Protected with Play → Play
Store distribution**. Ignore colons and letter case when comparing.

The app signing certificate is the identity users install. The **upload key**
only authenticates an AAB upload; matching the upload certificate alone does
not establish cross-store compatibility. Play has already been configured to
use the existing release key for this project; confirm the app signing
certificate before rollout. If Play shows multiple app signing certificates
because of a key upgrade, check device/version applicability before assuming
the same compatibility on every Android version.

For a new app that has not selected its key yet, choose **provide/upload an
existing app signing key** and follow Play's PEPK instructions locally using
the existing release keystore. Do not choose a new Google-generated key when
the goal is retaining the existing GitHub identity. If Google already generated
a different app signing key, resetting the upload key will not fix it; consult
the signing options still available in Play before publishing.

Keep the keystore and passwords private and backed up. F-Droid needs only the
public fingerprint and signed APK URL, never the private key. Existing GitHub
secrets remain `CB_KEYSTORE_BASE64`, `CB_STORE_PASSWORD`, `CB_KEY_ALIAS` and
`CB_KEY_PASSWORD`; do not replace them with a different key for F-Droid.

## 2. Publish the next stable GitHub release

`v1.0.5` is the release prepared for Obtainium and F-Droid submission. Its
universal APK and Play AAB use version code **32650**. The Android build omits
rclone's unused embedded web GUI using the `nogui` build tag. The rclone fork
retains its normal GUI in default builds. Do not move existing tags or replace
published APKs. For later releases:

1. Finish the release changes. Update `versionName` and `versionCode` together
   in `app/build.gradle`, and add `docs/releases/<version>.md`.
2. The default code must equal **release commit count × 20 + 10**, using full
   Git history. Include the release commit itself in that count. For one new
   commit on the current branch, calculate `(git rev-list --count HEAD + 1) *
   20 + 10`; recalculate if the planned history changes. Codes end in zero.
3. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, at most
   500 characters. The shared Fastlane descriptions, icon and screenshots are
   used by the stores. Check that they describe the release accurately and
   that all assets have redistribution rights.
4. Run `./gradlew testOssDebugUnitTest`,
   `./gradlew lint -x :rclone:buildAll` and `./gradlew assembleOssDebug`.
5. Commit the reviewed changes, push `master` when ready, and wait for its
   Android Release Build to pass. A push updates the development prerelease.
6. Tag the exact release commit `v<version>` and push that tag. The workflow
   tests, lints, builds and checks the release signatures, then publishes a
   stable GitHub release with five APKs, an AAB and `SHA256SUMS`.
7. Check the workflow completed and the intended version is GitHub's **Latest**.
   Download the assets and run `sha256sum -c SHA256SUMS` in their directory.
   Verify the universal APK with the SDK's `apksigner verify --print-certs` and
   `aapt dump badging`: require the package ID, version, code and certificate
   above. Read the workflow logs to record the actual JDK, Go and Android
   build-tools versions used by this release.

See [release workflow details](releases/README.md). This preparation does not
create a release, commit, tag or push automatically.

## 3. Upload that release to Google Play

1. Download `cloudbridge_v<version>-oss-release.aab` from the verified stable
   GitHub release. Use that release's AAB rather than a development artifact.
2. In Play Console, open **Testing → Internal testing → Create new release**.
   Upload the AAB and add the release notes. Every new uploaded release needs
   a previously unused, increasing version code.
3. Complete the listing, screenshots, contact details, privacy-policy URL,
   content rating, target audience, ads and Data safety sections based on the
   app's actual behavior. The privacy-policy URL is
   `https://thies2005.github.io/CloudBridge/privacy.html`.
4. Complete the all-files-access declaration for `MANAGE_EXTERNAL_STORAGE`,
   explaining the file-manager and sync features. Check the current Console
   dashboard for any additional declarations or testing requirements applicable
   to your developer account; do not assume internal testing bypasses them.
5. Roll out to internal testers. Install through the **internal testing track**
   and test access, provider login and sync. **Internal app sharing** is a
   separate feature with a different signing identity and is not a certificate
   compatibility test. Compare the installed app's certificate, or inspect a
   signed APK downloaded from the bundle explorer, against the fingerprint.
6. Promote the tested release through the required testing tracks and then
   production; submit the changes for Google's review.

For future releases repeat this using the same signing identity and a greater
code. You do not upload an APK to Play for this AAB-based workflow.

## 4. Submit the developer-signed build to official F-Droid

The draft recipe is
[`fdroid/metadata/de.schuelken.cloudbridge.yml`](../fdroid/metadata/de.schuelken.cloudbridge.yml).
It is intentionally disabled and targets `v1.0.5`, version code **32650**.
After publication, replace its `commit: v1.0.5` with the tag's **full immutable
source commit SHA** in your fdroiddata submission. Keep the version and code
unchanged for that release. A tag cannot embed its own future commit SHA in
the source it identifies.

`Binaries` points to the signed stable universal APK, and
`AllowedAPKSigningKeys` pins the existing certificate. With this combination,
F-Droid distributes your original signed APK only after successfully rebuilding
and verifying it. A failed comparison must block publication; do not remove
these fields or switch to an F-Droid signing key to bypass it.

1. Create a GitLab account and fork
   [fdroiddata](https://gitlab.com/fdroid/fdroiddata). Clone your fork and create
   a branch for CloudBridge.
2. Copy the draft to `metadata/de.schuelken.cloudbridge.yml` in that checkout.
   Replace the build and `CurrentVersion` values with the prepared stable
   release. Ensure the selected commit includes `scripts/prepare-fdroid.sh` and
   the offline/unsigned Gradle options. Keep the certificate pin unchanged.
3. Provision the release's toolchain in the F-Droid environment: **JDK 17**,
   exact **Go 1.26.0**, SDK platform 36 and **NDK 29.0.14206865** for the current
   configuration. Read `gradle.properties` at the selected tag for future
   releases. Install Go outside the source tree, put it on `PATH`, and match
   the actual JDK patch and Android build-tools used by GitHub. Those latter
   versions are not yet pinned by this repository, so a successful comparison
   must establish them before calling the recipe reproducible.
4. Run the preparation script with network access. It checks the exact Go
   version, fetches the pinned public rclone source and downloads its Go
   modules. The draft calls it from `prebuild`. The subsequent native rebuild
   uses local dependencies with Go's proxy, checksum lookup and automatic
   toolchain download disabled. Gradle dependencies must also be provisioned
   before attempting a completely network-disabled Gradle build.
5. Review F-Droid's source and dependency scan, including the prefetched Go
   module ZIPs and dependency test fixtures. Toolchain availability and any
   scanner findings need resolution with the packagers. No blanket
   `scanignore` is supplied and this preparation is not a dependency/license
   audit. Do not include downloaded compiler binaries in the source cache.
6. Once the environment and new release values are correct, remove the draft
   build's `disable` entry **in your fdroiddata fork**, then run:

   ```sh
   fdroid readmeta
   fdroid rewritemeta de.schuelken.cloudbridge
   fdroid lint de.schuelken.cloudbridge
   fdroid build --verbose de.schuelken.cloudbridge
   ```

7. Require successful package/version checks and compare the unsigned output
   with the signed universal APK from that exact release. For a separate local
   comparison, install the `apksigcopier` tool and run:

   ```sh
   apksigcopier compare --unsigned upstream-signed.apk locally-built-unsigned.apk
   ```

   Investigate mismatches with `diffoscope`. A local comparison is useful, but
   the independent F-Droid server rebuild and publication verification remain
   the acceptance gate. Keep ordinary APK splits enabled and select the
   universal output; `-PnoAbiSplits` is only for the AAB build.
8. Commit the tested metadata to your fork and open a merge request to
   `fdroid/fdroiddata`, titled `New App: CloudBridge`. Explain the fork's
   Internxt/Drime support and maintenance improvements, link the source and
   release, and state that **developer-signed reproducible distribution is
   required from the first release**. Respond to build and policy review.
   Alternatively open a [packaging request](https://gitlab.com/fdroid/rfp/-/issues)
   with the same information and link this draft.
9. Wait for acceptance, successful server verification and repository
   publication. Only then add an F-Droid installation badge. Further releases
   initially need new tested build entries in fdroiddata; auto-update is
   disabled until the initial recipe and toolchain are established. Review
   inherited upstream tags before enabling automatic tag checks.

For a local unsigned verification build after preparation:

```sh
bash scripts/prepare-fdroid.sh
./gradlew :app:assembleOssRelease -PunsignedRelease=true -PrcloneOffline=true --no-daemon
```

`unsignedRelease` deliberately produces an unsigned APK without reading signing
credentials. It is **only for verification**, not a distributable release.
Normal GitHub CI still requires the existing private key. `rcloneOffline`
checks the pinned Git revision and rejects tracked and untracked source changes.
The offline clean task preserves source/modules and removes compiled native
libraries. F-Droid source tarballs omit `.git`, so rebuilding that archive alone
is not yet supported: start from the tagged application checkout and fetch the
pinned rclone checkout again during network-enabled preparation. The application
checkout also provides the revision embedded by the Android build tools.
Do not silently skip source verification for a tarball rebuild.

## 5. Set up Obtainium

1. Install [Obtainium](https://obtainium.imranr.dev/) on the Android device.
2. Open **Add CloudBridge to Obtainium** in the repository README or project
   website. It opens Obtainium's import dialog with the configuration in
   [`docs/obtainium.json`](obtainium.json); review it and confirm.
3. The configuration follows `https://github.com/thies2005/CloudBridge`,
   excludes prereleases, sorts by release date, and selects only
   `cloudbridge_v<version>-oss-universal-release.apk`. It excludes development
   builds, AABs, other architectures and checksums.
4. Install/update when Obtainium offers an eligible newer release. Allow its
   Android installation permission when prompted; configure background update
   checks in Obtainium according to your preferences.

If the link cannot open, use **Add app** and paste the GitHub repository URL.
Keep **Include prereleases** off, choose date sorting and set the APK filename
filter to:

```regex
^cloudbridge_v[0-9]+\.[0-9]+\.[0-9]+-oss-universal-release\.apk$
```

Disable automatic architecture filtering when using this universal-only rule.
The JSON file is a single app configuration for Obtainium's app importer;
it is not a full-device backup or a separate update feed. No app-directory
submission is required. If changing the JSON, regenerate the URL-encoded app
payload in both the README and website so the links stay in sync.

## Switching channels without losing data

The application ID, compatible signing identity and Android version-code
ordering all matter. Universal APKs and the Play AAB use the base code; ABI APKs
add 6–9. Switching from an ABI APK to Play/universal at the **same release** can
therefore be a downgrade. Wait for the next stable release with a greater code;
do not uninstall just to change channels. A development build from a later
commit can also have a higher code than the current stable release. Export
important configuration before any deliberate uninstall.

## Official references

- [Play app signing and distribution outside Play](https://support.google.com/googleplay/android-developer/answer/9842756)
- [F-Droid submission guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
- [F-Droid reproducible builds and upstream signatures](https://f-droid.org/docs/Reproducible_Builds/)
- [F-Droid build metadata](https://f-droid.org/docs/Build_Metadata_Reference/)
- [Obtainium import links](https://wiki.obtainium.imranr.dev/deep_links/)
