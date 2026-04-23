# CloudBridge — Agent Notes

Android cloud file manager wrapping [rclone](https://rclone.org). Fork of RCX / rcloneExplorer.

## Build

**Prerequisites**: Go 1.25+, JDK 17, Android SDK with NDK. Versions are pinned in `gradle.properties` — check there first if builds break.

```sh
# Debug (CI uses this)
./gradlew assembleOssDebug

# Release
./gradlew assembleOssRelease
```

- Two product flavors: **`oss`** and **`rs`** (dimension: `edition`). Almost all work targets `oss`.
- APK output: `app/build/outputs/apk/oss/debug/`
- ABI splits: armeabi-v7a, arm64-v8a, x86, x86_64, universal

## Module structure

| Module | Purpose |
|---|---|
| `app` | Main Android application |
| `rclone` | Cross-compiles rclone (Go) into `librclone.so` per ABI |
| `safdav` | SAF/WebDAV bridge library (`io.github.x0b.safdav`) |

`app:preBuild` depends on `:rclone:buildAll`, so the app build automatically triggers rclone cross-compilation. First build downloads and caches rclone source in `rclone/cache/`.

## rclone source

The rclone binary is built from the fork at `https://github.com/thies2005/rclone` (which includes Internxt auto-token-renewal). Source is controlled by two properties in `gradle.properties`:

| Property | What it controls |
|---|---|
| `rCloneRepoUrl` | Git remote URL to clone from |
| `rCloneRef` | Branch, tag, or commit to checkout and build |

**To upgrade the fork**: change `rCloneRef` (e.g. to a newer tag or commit), then rebuild. The build script will `git fetch` + `git checkout` on every build, so no manual cache clearing is needed.

The old `rclone/patches/` directory is no longer used — the fork contains the Internxt backend changes directly.

## Lint

```sh
./gradlew lint -x :rclone:buildAll
```

Lint skips rclone compilation to save time. Lint baselines exist (`lint-baseline.xml` in `app/` and `safdav/`). `abortOnError` is enabled; `MissingTranslation` is demoted to warning.

## Testing

Minimal test coverage — only two unit tests in `app/src/test/`. Run with:

```sh
./gradlew testOssDebugUnitTest
```

No instrumented/androidTest runner is wired in CI.

## Architecture / source layout

- **Package namespace**: `ca.pkay.rcloneexplorer` (legacy from rcloneExplorer fork)
- **Application ID**: `de.schuelken.cloudbridge`
- Newer code lives under `de.schuelken.cloudbridge.*`
- `app/src/rcx/` — additional source set (RCX-specific utilities)
- `rclone/patches/` — **DEPRECATED**, no longer used. Fork already contains Internxt backend.
- The rclone binary is statically compiled with `CGO_ENABLED=0` and shipped as `librclone.so` per ABI in `app/lib/`.

## Key version pins (`gradle.properties`)

| Property | What it controls |
|---|---|
| `de.schuelken.cloudbridge.goVersion` | Go toolchain version (informational) |
| `de.schuelken.cloudbridge.rCloneVersion` | Fallback rclone version (if VERSION file unreadable) |
| `de.schuelken.cloudbridge.rCloneRepoUrl` | Git URL for the rclone fork |
| `de.schuelken.cloudbridge.rCloneRef` | Git ref (branch/tag/commit) to build from |
| `de.schuelken.cloudbridge.ndkVersion` | Android NDK version for cross-compilation |
| `de.schuelken.cloudbridge.ndkToolchainVersion` | NDK toolchain API level |

## CI workflows

- **`android.yml`**: Builds debug APKs on push to master; uploads per-ABI artifacts.
- **`lint.yml`**: Runs lint on every PR (not on master).
- **`dependencies.yml`**: Rebuilds on `build.gradle` changes and runs FOSS library scan.
- **`translations.yml`**: Profanity-checks translated `strings.xml` on PRs.

## Gotchas

- **Windows builds**: The rclone module handles Windows-specific NDK paths (`.cmd` suffixes, CRLF→LF conversion on patched Go files).
- **Debug applicationId**: Debug builds append `.debug` to the application ID, so debug and release can coexist on a device.
- **`versionCode`**: Last digit is reserved for ABI multiplier — version codes end in `0`.
- **Version Updates**: Always update the small versions (patch version and `versionCode`) with each build.
- **`local.properties`** with `sdk.dir` or `ANDROID_HOME` env var is required for rclone cross-compilation.
- Translations are managed via Weblate and Crowdin — don't manually edit localized `strings.xml` unless adding a new language.
