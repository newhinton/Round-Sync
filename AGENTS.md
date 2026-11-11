# CloudBridge Agent Guidelines

Android cloud file manager wrapping [rclone](https://rclone.org). Fork of RCX / rcloneExplorer.

## Core Rules

- Ask clarifying questions only when requirements are ambiguous, risky, conflict with existing patterns, or have meaningful tradeoffs. Do not ask for trivial tasks.
- Make the smallest correct change. Do not refactor unrelated code or over-engineer.
- Follow this codebase's existing patterns before applying generic best practices.
- If best practice and minimal change conflict, ask the user before proceeding.
- Use targeted searches before reading large files.
- Never commit secrets, credentials, API keys, or unrelated user changes.

## Project Structure

| Module | Purpose |
|---|---|
| `app` | Main Android application |
| `rclone` | Cross-compiles rclone (Go) into `librclone.so` per ABI |
| `safdav` | SAF/WebDAV bridge library (`io.github.x0b.safdav`) |

- Package namespace: `ca.pkay.rcloneexplorer` (legacy from rcloneExplorer fork).
- Application ID: `de.schuelken.cloudbridge`.
- Newer code lives under `de.schuelken.cloudbridge.*`.
- `app/src/rcx/` is an additional source set for RCX-specific utilities.
- Product flavors are `oss` and `rs` in the `edition` dimension. Most work should target `oss` unless the task says otherwise.

## rclone Upgradeability

Keep this repository easy to upgrade from upstream rclone.

- The rclone source is controlled by `de.schuelken.cloudbridge.rCloneRepoUrl` and `de.schuelken.cloudbridge.rCloneRef` in `gradle.properties`.
- To upgrade rclone, prefer changing only `rCloneRef` (and `rCloneRepoUrl` only if switching forks), then rebuild.
- Do not modify generated or fetched rclone source under `rclone/cache/`.
- Do not reintroduce the deprecated `rclone/patches/` flow. The fork already contains project-specific rclone changes, including Internxt auto-token-renewal.
- Prefer Android-side integration changes in `app/` over Go-side changes in rclone.
- If a Go-side rclone change is unavoidable, keep it in the rclone fork at `https://github.com/thies2005/rclone`; do not vendor local source patches here.

## Build

Prerequisites: Go 1.25+, JDK 17, Android SDK with NDK. Versions are pinned in `gradle.properties`; check there first if builds break.

```sh
./gradlew assembleOssDebug
./gradlew assembleOssRelease
```

- `app:preBuild` depends on `:rclone:buildAll`, so app builds trigger rclone cross-compilation.
- First build downloads and caches rclone source in `rclone/cache/`.
- APK output is under `app/build/outputs/apk/oss/debug/`.
- ABI splits: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`, `universal`.

## Verification

Run the checks that match the change. Before any commit or push, required checks must pass or the failure must be explained to the user.

```sh
./gradlew testOssDebugUnitTest
./gradlew lint -x :rclone:buildAll
./gradlew assembleOssDebug
```

- Unit test coverage is minimal and lives in `app/src/test/`.
- No instrumented/androidTest runner is wired in CI.
- Lint baselines exist in `app/` and `safdav/`; `abortOnError` is enabled and `MissingTranslation` is a warning.
- Skip `assembleOssDebug` only for docs-only changes that cannot affect the build.

## Bug And Feature Workflow

1. Research the codebase to find the root cause or integration point.
2. Create a short plan when the change is non-trivial. List files to change, intended logic, and expected effect.
3. Implement only the smallest correct change.
4. Review the result for correctness, architectural consistency, edge cases, regressions, performance, and security.
5. If using an agent tool that supports subagents, use one for plan or code review on non-trivial changes.
6. If review finds issues, fix them and re-review. If issues persist, revert only your own changes and ask the user how to proceed.

## Versioning And Git

- Version codes end in `0`; the last digit is reserved for ABI multipliers.
- For release/build-version work, update the patch version and `versionCode` together.
- Use Conventional Commits for commit messages, such as `fix: correct login validation` or `feat: add Internxt token refresh`.
- Do not push unless explicitly asked.

## CI Workflows

- `android.yml`: Builds **release** APKs on push to `master`; uploads per-ABI beta-release artifacts.
- `lint.yml`: Runs unit tests + lint on every PR, not on `master`.
- `dependencies.yml`: Rebuilds on `build.gradle` changes and runs FOSS library scan.
- `translations.yml`: Profanity-checks translated `strings.xml` on PRs.

## Gotchas

- Windows builds require the rclone module's Windows-specific NDK handling (`.cmd` suffixes and CRLF to LF conversion).
- Debug builds append `.debug` to the application ID, so debug and release can coexist on a device.
- `local.properties` with `sdk.dir` or `ANDROID_HOME` is required for rclone cross-compilation.
- Translations are managed via Weblate and Crowdin; do not manually edit localized `strings.xml` unless adding a new language.
