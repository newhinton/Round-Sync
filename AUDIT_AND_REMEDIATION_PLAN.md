# CloudBridge — Audit Report & Multi-Phase Remediation Plan

> **Status:** PLAN ONLY. Nothing implemented. Each phase is designed for coordinated track execution with a mandatory review gate before progression.
>
> **Method:** 5 parallel specialist audit agents (Bugs, Code-Quality/Features, UI/UX, Security, Build/CI) produced 118 raw findings. An independent review agent family re-verified every finding against the live code: **105 CONFIRMED, 10 PARTIAL (severity-nudged), 2 FALSE POSITIVE (dropped)**. Root-cause deduplication collapsed the 118 to **41 distinct remediation items**.
>
> **Second-pass audit note:** This plan was re-checked against the current repository. The confirmed backlog remains directionally sound, but the original execution guidance had several issues: some tracks were not actually file-disjoint, RC-3 was marked High but left unscheduled, the signing-key remediation omitted CI secret provisioning, the manifest hardening advice was too broad, and the update-download fix understated the filename/asset-resolution problem. Those corrections are folded into this version.

---

## 0. Methodology & Review Outcome

| Section | Raw | Confirmed | Partial | False Positive |
|---|---|---|---|---|
| BUG (crashes/leaks) | 27 | 25 | 0 | 2 (BUG-018, BUG-025) |
| CQ (code quality/features) | 23 | 21 | 2 (CQ-013, CQ-017) | 0 |
| UI (UI/UX/a11y) | 33 | 26 | 6 | 0 |
| SEC (security) | 11 | 10 | 1 (SEC-006 → Low) | 0 |
| BUILD (build/CI) | 24 | 23 | 1 (BUILD-012 → Low) | 0 |
| **Total** | **118** | **105** | **10** | **2** |

### Dropped as FALSE POSITIVE (do not fix)
- **BUG-018** — `TriggerService.onStartCommand` null-intent NPE. `onStartCommand` returns `START_NOT_STICKY` (`TriggerService.java:190`); the system does not redeliver a null intent. (A defensive null-check is still harmless, but the cited crash is impossible.)
- **BUG-025** — `Rclone.copyConfigFile` "always throws IOException". The double negation `!(renameTo && !delete())` is correct: on success renameTo=T, delete()=F, `!F`=T, `T&&T`=T, `!T`=F → no throw. Logic is gnarly but correct.

### Severity downgrades (PARTIAL)
- **SEC-006** Medium → **Low**: AppCenter code in `app/src/rcx/` is never compiled (rcx is not a registered sourceSet); only an unused `BuildConfig.CLI` UUID ships.
- **BUILD-012** Medium → **Low**: JitPack deps ARE pinned to tags (claim "unpinned" was wrong); residual concern is JitPack mutability only.
- **UI-020** (logs/tasks/trigger empty states): only `fragment_logs.xml` truly lacks empty state; tasks/trigger have one.
- Others (UI-008, UI-013, UI-015, UI-016, UI-024, CQ-013, CQ-017): substance confirmed, counts/locations refined — no severity change.

### Cross-section duplicates (collapsed into single items below)
- Keystore: **SEC-001 = BUILD-001 = CQ-016**
- Leaked probe process: **BUG-002 = SEC-010**
- `cancelAllWork()`: **BUG-005 = CQ-012**
- Update receiver cluster: **BUG-015 = CQ-009 = CQ-019 = SEC-009**
- Exported components: **SEC-004 ⊇ CQ-018**
- Dep mismatches: **CQ-015 = BUILD-005 = BUILD-006 = SEC-008**
- AsyncTask: **UI-024 = CQ-014**
- Go-version docs: **CQ-010 = BUILD-024**

### Second-pass critique and corrections

This section records plan-quality issues found while auditing the audit. These are not new product bugs; they are corrections to how the remediation work should be executed.

- **The core findings are mostly credible.** Live-code spot checks confirmed the hardcoded release signing config, `allowBackup=true`, exported internal components, duplicate FileProviders, rclone `master` checkout, dependency workflow prefix/glob drift, `RcdService.waitOnline` null synchronization, `UpdateWorker` returning before its coroutine completes, `getRuntimeProcess` spawning a leaked probe process, and the `getDisplayNameCompat` inverted null check.
- **Phase parallelism was overstated.** Phase 1 tracks both touch `Rclone.java`, and the update/manifest/FileProvider work touches related install-flow code. Treat tracks as parallelizable only when owners coordinate same-file edits and merge order.
- **RC-3 could not stay in the unscheduled backlog.** At-rest credential encryption is a High security item. It needs at least a design/migration gate before the final release, even if implementation is larger than the crash fixes.
- **Signing remediation must include CI.** Removing `app/.config/android/roundsync.keystore` without adding CI secret provisioning breaks `.github/workflows/android.yml`, which runs `assembleOssRelease`.
- **The signing Gradle advice must be Groovy-correct.** Use `?:`, Gradle providers, or explicit property checks; do not use the invalid `?, ""` fallback. Release signing should only attach when all secret values are present, so local unsigned/debug builds remain possible.
- **History rewrite is not a complete incident response.** Scrubbing git history reduces future exposure but does not revoke clones, forks, downloaded APKs, or already-installed old-key apps. The release process must publish old/new signing certificate fingerprints and tell users that old-key updates are no longer trusted.
- **Manifest hardening must be component-specific.** Do not blindly add a custom permission to every exported component. Public entry points such as `MainActivity`, `SharingActivity`, the documents provider, and the boot receiver have different contracts from internal notification/action receivers. Prefer `exported="false"` for app-private components; use an app-defined `signature` permission only where another same-signature app genuinely needs access. Do not use `signature|privileged` for normal app components.
- **The update-download issue is bigger than `unviversal`.** `UpdateUserchoiceReceiver` constructs `CloudBridge_$version-oss-$abi-release.apk`, while Gradle outputs `cloudbridge_v<version>-oss-...` and CI uploads wildcarded release assets. The correct fix is to resolve the release asset from GitHub metadata, not to hardcode another filename.
- **WorkManager cancellation advice must not assume tags exist.** `SyncManager` has task-id tags, but `EphemeralTaskManager` currently enqueues work with an empty tag. Define stable tags or unique work names before replacing `cancelAllWork()`.
- **Delete undo must delay destructive work.** The current delete flow enqueues WorkManager jobs immediately. A Snackbar undo cannot reliably cancel once a worker starts; implement undo by deferring enqueue until the Snackbar timeout, or by adding real trash/restore semantics.
- **Remote type tests should use config-dump/backend fixtures.** `rclone listremotes` returns configured remote names, not backend type names. Test `RemoteItem.getTypeFromString()` and config-dump parsing against known backend type strings instead.

---

## 1. Deduplicated Remediation Backlog (41 root-cause items)

Each item below is one root-cause remediation item. Later phases group one or more items into implementation tracks. `Refs` = original finding IDs. Severity is post-review.

### 🔴 Critical (1)
| ID | Title | Refs | Primary file |
|---|---|---|---|
| **RC-1** | Release signing keystore committed to git, password `"android"` | SEC-001, BUILD-001, CQ-016 | `app/build.gradle:6-13`, `app/.config/android/roundsync.keystore` |

### 🟠 High — Security (4)
| ID | Title | Refs | Primary file |
|---|---|---|---|
| **RC-2** | `allowBackup=true` with no exclusions leaks `rclone.conf` + WebDAV pass | SEC-002 | `AndroidManifest.xml:28` |
| **RC-3** | Cloud creds/TOTP only rclone-obscured (reversible), no encryption at rest | SEC-003 | `Rclone.java:528`, `InternxtReauth.kt:67` |
| **RC-4** | App-private exported components lack permission/export guards → any app can trigger sync/install paths | SEC-004, CQ-018 | `AndroidManifest.xml:38-41,57-63,87-89,94-95,110-117` |
| **RC-5** | Serve "allow remote access" can bind `0.0.0.0` with no auth | SEC-005 | `Rclone.java:670-687`, `ServeDialog.java:71-79` |

### 🟠 High — Build / CI (3)
| ID | Title | Refs | Primary file |
|---|---|---|---|
| **RC-6** | `dependencies.yml` greps wrong property prefix + wrong APK glob (FOSS scan no-op) | BUILD-002 | `.github/workflows/dependencies.yml:37,55,73` |
| **RC-7** | `compileSdk`/`targetSdk` 34 below Play policy (needs 35+) | BUILD-003 | `app/build.gradle:20-21`, `safdav/build.gradle:6-7` |
| **RC-8** | rclone source pinned to moving branch `master` → non-reproducible | BUILD-004 | `gradle.properties:34`, `rclone/build.gradle:229-238` |

### 🟠 High — Crash / Correctness bugs (18)
| ID | Title | Refs | Primary file |
|---|---|---|---|
| **RC-9** | `TriggerActivity` NPE: no `return` after `finish()` on missing trigger | BUG-001 | `TriggerActivity.kt:101-103` |
| **RC-10** | `getRuntimeProcess` spawns + leaks a probe rclone process per call | BUG-002, SEC-010 | `Rclone.java:454-462` |
| **RC-11** | `Rclone.getConfig` NPE when remote missing (`optJSONObject` null) | BUG-003 | `Rclone.java:596-597` |
| **RC-12** | `SharingActivity` NPE: no `return` after `finish()` on null EXTRA_STREAM | BUG-004 | `SharingActivity.java:132-148` |
| **RC-13** | `cancelAllWork()` nukes every worker app-wide (3 sites) | BUG-005, CQ-012 | `EphemeralTaskManager.kt:113-116`, `SyncManager.kt:53-56`, `UpdateManager.kt:37` |
| **RC-14** | EphemeralWorker/SyncWorker unsynchronized mutable state across `doWork/onStopped` | BUG-006 | `EphemeralWorker.kt:78-196`, `SyncWorker.kt:77-148` |
| **RC-15** | `RcdService.waitOnline` NPE on `synchronized(null)` + null-Boolean unbox | BUG-007 | `RcdService.java:302-322` |
| **RC-16** | `RcdService.getLocalRcd()` not synchronized → double rcd spawn | BUG-008 | `RcdService.java:324-335` |
| **RC-17** | `MainActivity.onCreate` falls through after onboarding redirect (UI work on dead Activity) | BUG-009 | `MainActivity.java:120-224` |
| **RC-18** | `MainActivity.onActivityResult` NPEs on null `data`/null `getData()` | BUG-010 | `MainActivity.java:266-292` |
| **RC-19** | Hash/version methods NPE/IOOBE on empty rclone stdout (startup crash) | BUG-011, BUG-012 | `Rclone.java:1072-1145` |
| **RC-20** | `StreamingService`/`OauthHelper` call `exitValue()` after async `destroy()` | BUG-013, BUG-014 | `StreamingService.java:115-125`, `OauthHelper.java:96-102` |
| **RC-21** | `UpdateUserchoiceReceiver` cluster: exported, raw-Thread download, asset-name/ABI 404s, no checksum, no goAsync | BUG-015, CQ-009, CQ-019, SEC-009 | `UpdateUserchoiceReceiver.kt:39-94` |
| **RC-22** | `UpdateWorker` returns `Result.success()` before its coroutine runs | BUG-016 | `UpdateWorker.kt:32-85` |
| **RC-23** | `ThumbnailsLoadingService` NPE on null remote; literal `"null"` in path | BUG-017 | `ThumbnailsLoadingService.java:38-42` |
| **RC-24** | `Log2File` rotation threshold = 10 GB not 10 MB; NPE on null ext dir | BUG-026 | `Log2File.java:24,38-43` |
| **RC-25** | `getDisplayNameCompat` always returns null (inverted condition) | BUG-027 | `safdav/.../DocumentsContractAccess.java:312-323` |
| **RC-26** | FD/Process/Parcel/BufferedReader leaks (FD exhaustion under load) | BUG-020, BUG-023 | `EphemeralTaskManager.kt:92-102`, `Rclone.java:304-359` |

### 🟠 High — Code Quality / Features (5)
| ID | Title | Refs | Primary file |
|---|---|---|---|
| **RC-27** | Duplicate `<provider>` entries, same `${applicationId}.fileprovider` authority | CQ-001 | `AndroidManifest.xml:119-127,236-244` |
| **RC-28** | `RemoteItem` frozen at rclone 1.50.2; no Internxt/Drime entries (wrong icons/flags) | CQ-004, CQ-008 | `RemoteItem.java:30,281-355` |
| **RC-29** | Half-finished namespace migration `ca.pkay` ↔ `de.schuelken` (parallel impls) | CQ-005 | cross-tree |
| **RC-30** | TODOs encode live bugs (VCP `remotes/remotes` path, NetworkOnMainThread, EphemeralWorker placeholder FileItem) | CQ-020 | `VirtualContentProvider.java:774,935`, `EphemeralWorker.kt:456` |
| **RC-31** | OkHttp 4.12 vs logging 4.9.1 skew + duplicate JSON stacks (Jackson + kotlinx) | CQ-015, BUILD-005, BUILD-006, SEC-008 | `app/build.gradle:175,184-189` |

### 🟠 High — UI/UX (9)
| ID | Title | Refs | Primary file |
|---|---|---|---|
| **RC-32** | Hardcoded English text incl. placeholders (`"Switch preference"`, `"fromid"`) shipping in UI | UI-001 | `settings_notification_preferences.xml:20`, `content_task.xml:307`, `fragment_tasks_item.xml:114+` |
| **RC-33** | `actionBarSize` dimen = `12dp` (typo) breaks tablet dialog toolbar | UI-003 | `values/dimens.xml:9`, `layout-sw720dp/dialog_remote_dest.xml` |
| **RC-34** | "Convenience" color aliases hardcode light theme; no `values-night/colors.xml` | UI-004, UI-029 | `values/colors.xml:72-83` |
| **RC-35** | Icons missing `contentDescription`; decorative icons not muted for TalkBack | UI-008, UI-009 | `fragment_remotes_item.xml:62-72`, `dialog_go_to.xml`, `settings_fragment.xml` |
| **RC-36** | Zero `labelFor` attributes → EditText fields unlabeled for TalkBack | UI-010, UI-011 | ~12 EditText across 11 layouts |
| **RC-37** | Destructive delete shows Toast only; no undo Snackbar | UI-018 | `FileExplorerFragment.java:1458-1475` |
| **RC-38** | `rclone.getRemotes()` on UI thread in `RemotesFragment.onCreateView` (ANR risk) | UI-023 | `RemotesFragment.java:107,343,372,585` |
| **RC-39** | Heavy deprecated `AsyncTask`/`IntentService`/`ProgressDialog` (27 subclasses/14 files) | UI-024, CQ-014 | cross-tree |
| **RC-40** | ~120 untranslated strings + 23 plural-quantity bugs + 21 `DefaultLocale` baselined | UI-030, UI-031, UI-033 | `lint-baseline.xml` |

### 🟡 Medium/Low — lower-priority backlog (not individually listed; tracked in §4)
Includes: god classes >1000 lines (CQ-006), three overlapping notification abstractions (CQ-007), deprecated legacy-support-v4 (BUILD-011), stale lint baseline 507 issues incl. security/policy (BUILD-010), GitHub Actions not SHA-pinned / compromised `tj-actions/changed-files@v44` (BUILD-009), no CI unit-test step (BUILD-008), Glide `annotationProcessor` in Kotlin project (BUILD-007), stale `rclone/patches/` dead code (BUILD-014), README/docs drift (BUILD-021/022/023/024, CQ-010, CQ-011), `SyncService` deprecated IntentService (CQ-002), toast-style error states (UI-021), no pagination (UI-026), no DiffUtil (UI-025), splash screen dep unused (UI-028), RTL `paddingLeft` (UI-007), `Reciever` typo (CQ-021), etc.

---

## 2. Execution Model

- **Phases** are sequential gates; **tracks** within a phase run in **parallel where ownership and file overlap allow it**.
- Each phase ends with a **Review Gate (RG)**: a code-review pass + the verification commands in `AGENTS.md`. The next phase does **not** start until RG passes.
- Tracks inside a phase should be handed to separate owners/PRs where possible, but they are **not guaranteed to be file-disjoint**. Same-file edits called out below must be merge-ordered or consolidated before review.
- Estimated effort: **S** ≈ half-day, **M** ≈ 1–2 days, **L** ≈ 3–5 days, **XL** ≈ 1+ week.

Execution rules:
- Do the smallest safe fix for each RC item; avoid opportunistic refactors unless a track explicitly calls for them.
- Before opening a track PR, re-run a targeted grep/read pass for the files named here because line numbers will drift as phases land.
- If a track touches signing, manifest exports, update installation, or credential storage, require a security-focused reviewer in addition to normal code review.
- Do not regenerate lint baselines to hide new issues. Regeneration is only acceptable after the underlying issue count is intentionally reduced or scoped.

Verification commands (run at every gate):
```sh
./gradlew testOssDebugUnitTest
./gradlew lint -x :rclone:buildAll
./gradlew assembleOssDebug
```

---

## 3. The Plan

### 🚪 PHASE 0 — Emergency Security Remediation (BLOCKING, serial, no parallelism)

> Rationale: RC-1 is an active, publicly-exploitable supply-chain compromise. It MUST be resolved and the key rotated before ANY other work merges, because every other fix ships under a possibly-counterfeit signature until this is done. Treat as an incident.

**S0-T1 (RC-1)** — Rotate & scrub signing key  · **effort: M**
- Generate a NEW release keystore outside the repository; store credentials in CI secrets and local developer properties only. Never place the keystore or passwords under `app/`, `.config/`, or any tracked path.
- Rewrite `app/build.gradle:6-13` in Groovy-safe form: read `CB_RELEASE_STORE_FILE`, `CB_RELEASE_STORE_PASSWORD`, `CB_RELEASE_KEY_ALIAS`, and `CB_RELEASE_KEY_PASSWORD` from Gradle properties or `System.getenv(...)`; use `?:` or explicit presence checks, not invalid `?, ""` syntax. Only attach `signingConfig signingConfigs.release` when all required values are present, so debug and unsigned local release builds still work.
- Update `.github/workflows/android.yml` in the same PR to provision the new keystore from secrets, e.g. base64 decode to a temporary file and pass only env/properties to Gradle. Without this, `assembleOssRelease` will fail after the tracked keystore is removed.
- Scrub history: `git filter-repo --path app/.config/android/roundsync.keystore --invert-paths` (or BFG). Force-push only after maintainer coordination, branch protection planning, and contributor rebase instructions.
- Treat old-key releases as compromised: remove old downloadable release artifacts where possible, publish old/new signing certificate fingerprints, and document that updates signed with the old key are no longer trusted. Existing side-loaded users cannot install a different signing key over the old app; they must uninstall/reinstall unless a store-controlled key-upgrade mechanism exists.
- Publish a signed release with the **new** key and clear migration notes before resuming feature releases.
- Also: `.gitignore` add `*.keystore`, `*.jks`, `.config/android/`.

**🔒 Review Gate RG-0** (blocking):
- Confirm `git ls-files | grep -iE 'keystore|\\.jks|\.config/android'` returns nothing.
- Confirm no tracked file contains the old store/key password strings.
- Confirm CI and a local clean checkout can build debug without signing secrets.
- Confirm a release APK builds in CI and signs with the new key only; record the SHA-256 certificate fingerprint in release notes.
- Sign-off required from maintainer before Phase 1.

---

### PHASE 1 — Critical Bugs + Security Hardening + Build Unblocking  (3 parallel tracks)

> Goal: stop the bleeding. Crashes, data-exfil vectors, and CI blind spots. Tracks can run in parallel by owner, but 1A and 1B both touch `Rclone.java`; merge-order those hunks or split `Rclone.serve()` hardening into a small shared PR.

#### Track 1A — Crash-class bug fixes  · **effort: M** (items: RC-9, RC-11, RC-12, RC-17, RC-18, RC-19, RC-23, RC-25)
Pattern uniting most of these: **"no `return` after `finish()`"** + **null-deref of rclone stdout**.
- `TriggerActivity.kt:101`, `SharingActivity.java:135`, `MainActivity.java:121` — add `return` after each `finish()`.
- `MainActivity.onActivityResult:266-292` — null-check `data` and `data.getData()` on all request codes, including `SETTINGS_CODE` before `data.getBooleanExtra(...)`; do not pass a null `Uri` to config import/export.
- `Rclone.java:596` — null-guard `configs.optJSONObject(name)`; `:1072,1109,1144` — guard null/empty stdout before `split`/`get(0)`.
- `ThumbnailsLoadingService.java:38` — null-check `remote`; sanitize null `getStringExtra`.
- `safdav/src/main/java/io/github/x0b/safdav/saf/DocumentsContractAccess.java:315` — fix inverted condition to `!cursor.isNull(0)`.
- **Do NOT** touch RC-15/16/20 here (they go to Track 1B/2).

#### Track 1B — Security hardening (config-only)  · **effort: M** (items: RC-2, RC-4, RC-5)
- `AndroidManifest.xml`: set `android:allowBackup="false"` unless a tested `dataExtractionRules`/`fullBackupContent` policy excludes `rclone.conf`, preferences, logs, and update APK cache. Prefer `false` for the emergency fix.
- Component-by-component export review: keep truly public components public (`MainActivity`, `SharingActivity`, documents provider with `android.permission.MANAGE_DOCUMENTS`, and boot receiver for `BOOT_COMPLETED`); set app-private components to `exported="false"` where possible (`TriggerService`, `TriggerReciever`, `SyncRestartAction`, and update notification actions if only internal). If same-signature external integrations are required, define a custom permission with `android:protectionLevel="signature"` only; do **not** use `signature|privileged`.
- `ShortcutServiceActivity` and `SyncService`: verify whether any launcher shortcut, pending intent, or external automation depends on public access before changing export state. If retained public, document the intent contract and require a signature permission.
- `Rclone.serve()` / `ServeDialog`: when "allow remote access" is checked, **require** non-empty user+pass (disable OK button or generate a random password surfaced to the user); never bind `0.0.0.0` unauthenticated. Coordinate this `Rclone.java` edit with Track 1A.
- RC-3 (encryption at rest) gets a design/migration gate in Phase 2 and implementation before final release.

#### Track 1C — Build/CI unblocking  · **effort: S** (items: RC-6, RC-7, RC-8)
- `dependencies.yml`: fix grep prefix `de.felixnuesse.extract.*` → `de.schuelken.cloudbridge.*`; fix APK glob `roundsync_v*` → `cloudbridge_v*`.
- Bump `compileSdk`/`targetSdk` 34 → 35 in `app/build.gradle` and `safdav/build.gradle`.
- `gradle.properties:34`: pin `rCloneRef` to a concrete immutable ref instead of `master`. If using a tag, update `rclone/build.gradle` because it currently clones with `--no-tags`; fetch tags or the exact ref explicitly. If using a commit SHA, prefer a full 40-character SHA and ensure the fetch refspec can retrieve it. Record the resolved SHA in build output, or in a lockfile only if the build verifies it.
- Build-007, 009, 010 (Glide ksp, SHA-pin actions, lint-baseline triage) deferred to Phase 2/3.

**🔒 Review Gate RG-1:**
- All three tracks merged via separate PRs (one per track) reviewed independently.
- Verification commands green; manual smoke: open a deleted trigger (RC-9), share with empty `EXTRA_STREAM` (RC-12), confirm Android/cloud backup cannot extract `rclone.conf`, confirm `assembleOssRelease` uses the pinned rclone source and does not require any tracked signing secret.
- Confirmed no regressions in `RemotesFragment` / config import flows.

---

### PHASE 2 — Correctness, Data Integrity & Resource Hygiene  (6 parallel tracks)

> Goal: fix the bugs that silently lose data, kill background work, or leak resources over time.

#### Track 2A — WorkManager correctness  · **effort: S** (items: RC-13, RC-22)
- Replace `WorkManager.cancelAllWork()` in `EphemeralTaskManager.cancel()`, `SyncManager.cancel()`, `UpdateManager.cancelAll()` with scoped cancellation. `SyncManager` already uses task-id tags; `UpdateManager` already uses unique periodic work name `TAG_REPEATING`; `EphemeralTaskManager` currently passes an empty tag and must first define stable operation tags or unique work names before cancellation can be scoped.
- `UpdateWorker`: convert to `CoroutineWorker` (or `runBlocking`), `return` the actual result; remove the orphan `CoroutineScope(Dispatchers.IO).launch`.

#### Track 2B — RcdService thread-safety  · **effort: S** (items: RC-15, RC-16)
- `RcdService.java:55` change `Boolean available` to a proper guard (`AtomicReference<Status>` or an explicit lock object, never synchronize on a boxed Boolean).
- `getLocalRcd()` → `synchronized` (or `ReentrantLock`) around the null-check + spawn.
- `waitOnline`: use `CountDownLatch` / `notifyAll` on a final lock object; never unbox a possibly-null Boolean.

#### Track 2C — Resource-leak & process-exit cleanup  · **effort: M** (items: RC-10, RC-20, RC-26)
- `Rclone.getRuntimeProcess:454-462` — delete the redundant `exec(rclone)` probe; keep the real `exec(command, env)`.
- `StreamingService:115-125`, `OauthHelper:96-102` — replace post-`destroy()` `exitValue()` with `waitFor(timeout, ...)` then `exitValue()`, or guard with `isAlive()`.
- `EphemeralTaskManager:92-102` — `parcel.recycle()` in `finally`.
- `Rclone.java` reader allocations (304-359, 373-391, 579-591, 1210-1228) — wrap in try-with-resources; `logErrorOutput` is the reference pattern.

#### Track 2D — Update flow rewrite  · **effort: M** (item: RC-21)
- Replace `UpdateUserchoiceReceiver`'s raw `Thread{}` download with a `WorkManager` job. If any receiver work remains asynchronous, use `goAsync()` correctly; otherwise keep the receiver as a thin enqueue-only component.
- Resolve APKs from GitHub release asset metadata instead of constructing `CloudBridge_$version-oss-$abi-release.apk`. Current build artifacts use `cloudbridge_v<version>-oss-<abi>-release.apk`; hardcoded filename repair will remain fragile.
- Use `Build.SUPPORTED_ABIS` with a tested universal fallback; fix `"unviversal"` → `"universal"`; add connect/read timeouts via OkHttp.
- Add SHA-256 verification against a trusted manifest or release asset metadata before invoking the installer. If GitHub does not publish checksums, add a release-generation step that does.
- Receive via the (Phase 1) signature-protected permission; keep `exported` if external triggers needed, else `false`.
- Add user-visible failure notification on download/verify error.
- Merge or remove the duplicate FileProvider in this track before testing installation, because update install uses `${applicationId}.fileprovider` and the manifest currently declares that authority twice.

#### Track 2E — TODO-encoded correctness bugs  · **effort: M** (item: RC-30)
- `VirtualContentProvider.java:774` — fix `remotes/remotes/...` → `remotes/...` path dedup.
- `VirtualContentProvider.java:935` — move cache-miss network call off main thread (NetworkOnMainThreadException).
- `EphemeralWorker.kt:456` — stop constructing upload FileItems with placeholder `0L/"modTime"/"mimeType"`; capture real metadata.
- `Log2File.java:38` — fix threshold `10000000` → `10000` (≈10 MB) and null-check `getExternalFilesDir`.

#### Track 2F — Credential-storage design gate  · **effort: M** (item: RC-3)
- Produce a short ADR before implementation: inventory exactly what secrets are stored in `rclone.conf`, preferences, logs, update cache, and exported/imported config files; define which data must be encrypted, which can remain rclone-obscured for compatibility, and how backup/export should behave.
- Choose an Android-side encryption approach (for example Android Keystore-backed Jetpack Security/Tink or a small Keystore wrapper) and document migration from existing plaintext/obscured configs. Do not make biometric unlock mandatory unless the UX explicitly supports recovery.
- Define failure modes: lost hardware-backed key, device restore, app reinstall, config import/export, and multi-profile devices.
- Land at least immediate guardrails in this phase: backup exclusion from Phase 1 verified by test/smoke, no secret values in logs, and no new preferences storing tokens outside the chosen design.
- Schedule implementation before final release; this item is no longer acceptable as an unscheduled backlog entry.

**🔒 Review Gate RG-2:**
- Per-track PRs reviewed.
- New unit tests added for: WorkManager cancel scope, RcdService single-spawn, FileItem mime extension (BUG-021), getDisplayNameCompat, and update asset selection from mocked release metadata.
- Manual smoke: start a sync, then disable updates in Settings → confirm sync keeps running (RC-13). Trigger an in-app update → confirm download progress + integrity check (RC-21).
- ADR accepted for RC-3, with implementation target and migration plan assigned.
- Verification commands green.

---

### PHASE 3 — UI/UX, Accessibility & Dark-Mode  (4 parallel tracks)

> Goal: user-visible quality. Tracks split by resource domain (colors/themes, strings/i18n, layouts/a11y, code-driven UX).

#### Track 3A — Theming & dark-mode color aliases  · **effort: M** (items: RC-34, RC-33, UI-006, UI-027, UI-028)
- Add `values-night/colors.xml` overriding `cardColor`, `iconTint`, `dividerColor`, `textColorPrimary/Secondary/Tertiary/Highlight`, `fabOverlayColor`, `colorAccent` to dark Material tokens. Mirror in `values-v31/colors.xml`.
- `values/dimens.xml:9` — `actionBarSize` `12dp` → `56dp`.
- `values/styles.xml:30-40` — `MainViewToolbar` → `?attr/colorOnPrimary`. `:42,:87` — repoint style parents to Material3 (`ThemeOverlay.Material3.Dark.ActionBar`, `MaterialAlertDialog.Material3`).
- `values-night/styles.xml` — rework `SecondaryCardStyle` to avoid `colorSurfaceBright` (M3 ≥1.2 only).
- Splash: call `installSplashScreen()` in the first Activity; set `windowSplashScreenAnimatedIcon`.

#### Track 3B — Strings & i18n  · **effort: M** (items: RC-32, RC-40, UI-019, UI-032)
- Replace every hardcoded `android:text=`/`setTitle(...)` literal in §RC-32 with `@string` entries; remove the `"Switch preference"` placeholder.
- Add `String.format(..., Locale.US)` / `Locale.ROOT` for the 21 `DefaultLocale` sites; convert selection-title concatenation to `<plurals>`.
- Push the new default-locale keys to Crowdin/Weblate. Do not add new `MissingTranslation` entries to the baseline; after fixes, regenerate only if the baseline shrinks or the remaining entries are explicitly accepted.

#### Track 3C — Accessibility & layout polish  · **effort: M** (items: RC-35, RC-36, UI-012, UI-007, UI-014, UI-015)
- Add `android:contentDescription` to all interactive ImageButtons (`remote_options`, `dialog_go_to` icons, pin icon w/ state description); add `android:importantForAccessibility="no"` to the 8 decorative settings icons.
- Add `android:labelFor="@id/...` from each EditText's label TextView; link the `dialog_go_to` checkbox label.
- Enforce 48dp `minWidth`/`minHeight` on `bottom_bar`/`move_bar` tap targets.
- Replace `paddingLeft`→`paddingStart`. Migrate legacy `CardView`→`MaterialCardView` and `SwitchPreference`→`SwitchPreferenceCompat` (12 sites).

#### Track 3D — Code-driven UX (delete undo, empty/error states, UI-thread I/O)  · **effort: L** (items: RC-37, RC-38, UI-020, UI-021)
- `FileExplorerFragment.deleteFiles` — implement undo by delaying `EphemeralTaskManager.queueDelete(...)` until the Snackbar timeout. Do not enqueue immediately and hope `cancelAllWorkByTag` wins the race; once a delete worker starts, undo requires real trash/restore semantics.
- Add a reusable empty-state + error-state view pair; wire `RemotesFragment`/`FileExplorerFragment` to show it (with Retry) instead of blanking on `getDirectoryContent == null`.
- **RC-38**: move `rclone.getRemotes()` off the UI thread in `RemotesFragment.onCreateView` (and the 5 other call sites) — load asynchronously into a ViewModel, show a loading state. *(This is the smallest on-ramp to RC-39/AsyncTask migration and should use coroutines, not another AsyncTask.)*
- `fragment_logs.xml` — add empty state.

**🔒 Review Gate RG-3:**
- Visual QA in both light & dark mode on phone + tablet (sw720).
- TalkBack pass over the remotes screen, a dialog with EditText, and the file explorer.
- Verify no new `MissingTranslation` abort; verification commands green.

---

### PHASE 4 — Architecture & Tech-Debt Reduction  (4 parallel tracks, larger)

> Goal: structural changes that unblock future work. Higher regression risk → each track needs its own PR + targeted tests.

#### Track 4A — RemoteItem type system + Internxt/Drime  · **effort: M** (item: RC-28)
- Decide: either (a) delete the int type system and migrate `equals/hashCode/icon/feature-flags` to a string-keyed enum/map, or (b) extend `getTypeFromString` to all rclone-1.74 remote types including `internxt`, `drime`. Add per-provider icon + correct `isOAuth/hasTrashCan/hasSyncSupport` flags.
- Add unit tests for `RemoteItem.getTypeFromString()` and `Rclone.getRemotes()` config-dump parsing using backend type fixtures/docs. Do not use `rclone listremotes` for this assertion; it returns configured remote names, not backend type names.

#### Track 4B — Dead code and legacy service cleanup  · **effort: S** (items: CQ-002, CQ-003, BUILD-014, BUILD-016, BUILD-017)
- Confirm RC-27 was already resolved in Phase 2D. If not, block this track and merge/delete the duplicate `<provider>` before any release candidate.
- Remove: dead `rclone/patches/internxt/` Go sources, `Log.e("TAG",...)` leftovers, dead commented Intent blocks, unused `ext.targetCompatibility=VERSION_11`, no-op `fileTree('libs')`.
- Decide `SyncService` fate: either document its public-intent contract and keep, or deprecate in favor of WorkManager (typo `"SYNC_SERCVICE"` action must be migrated if kept).

#### Track 4C — Dependency cleanup  · **effort: S** (items: RC-31, BUILD-007, BUILD-011, BUILD-015)
- Align `logging-interceptor` to `4.12.0` (or drop to `debugImplementation` only).
- Pick ONE JSON stack (recommend kotlinx-serialization since Kotlin-first); remove Jackson.
- Replace `legacy-support-v4` with the granular AndroidX artifacts actually used.
- Replace `streamsupport` with `coreLibraryDesugaring`.
- Switch Glide compiler to `ksp` (or remove the line entirely since no `@GlideModule` exists).

#### Track 4D — AsyncTask → coroutines migration  · **effort: XL** (items: RC-39)
- Introduce a `Dispatchers.IO` + `viewModelScope`/`lifecycleScope` convention. Migrate the 27 AsyncTask subclasses file-by-file (FileExplorerFragment's 7 first — highest leak/ANR risk). Each file = its own PR.
- Convert `IntentService`s (`StreamingService`, `ThumbnailsLoadingService`) to `LifecycleService` + coroutines or `WorkManager`.

**🔒 Review Gate RG-4:**
- All targeted unit tests green; instrumented test scaffolding (currently missing — CQ-017) added for `RemoteItem`, `Rclone` process wrapper, and at least one migrated AsyncTask→coroutine path.
- APK size delta reported (expect reduction from Jackson removal).
- Verification commands green.

---

### PHASE 5 — CI Quality Gates, Polish & Docs  (3 parallel tracks)

> Goal: prevent regression of everything above.

#### Track 5A — CI hardening  · **effort: M** (items: BUILD-008, BUILD-009, BUILD-010, BUILD-019)
- Add `./gradlew testOssDebugUnitTest` to `lint.yml` (PR-gated).
- SHA-pin every `uses:` in `.github/workflows/*` (use Renovate config to maintain). Remove `fabasoad/translation-action@main`; replace `tj-actions/changed-files@v44` with a SHA or a non-compromised alternative.
- Triage the 507-entry lint baseline: fix or scope each `<issue>` individually (especially `ScopedStorage`, `ExportedReceiver×3`, `ExportedService×2`, `NewApi×5`, `StaticFieldLeak×3`). Regenerate baseline to only contain acceptable items.
- `rclone/build.gradle:212-217` — make the Go-version check `throw new GradleException(...)` instead of `logger.error`.

#### Track 5B — Docs reconciliation  · **effort: S** (items: BUILD-021, BUILD-022, BUILD-023, BUILD-024, CQ-010, CQ-011)
- `README.md` — fix Go 1.20 → 1.25; fix license-badge URL (points at `newhinton/extract`).
- `BUILD_GUIDE.md` — remove fictional `usePrebuiltRclone`, fix `assembleDebug`→`assembleOssDebug`, fix Go version.
- `WINDOWS_BUILD_GUIDE.md` — fix Go 1.19.8, rclone version, remove "Apply Session Guardian patches" step, fix per-ABI Gradle task, fix APK glob.
- `AGENTS.md` CI section — `android.yml` builds **release**, not debug.

#### Track 5C — Lower-priority UI/UX backlog  · **effort: M** (items: UI-005, UI-013, UI-025, UI-026, UI-022, UI-002, CQ-021, CQ-023)
- Migrate `Toolbar`→`MaterialToolbar` (13 sites).
- Introduce `ListAdapter` + `DiffUtil` for the 6 RecyclerView adapters; add paged loading to directory listings.
- Consistent edge-to-edge across Activities (prep for `targetSdk 35`).
- Rename `Reciever`→`Receiver` (3 classes + manifest entries — migration commit).
- Tablet (`sw720dp`) layout coverage expansion for Settings/Tasks/Triggers.

**🔒 Review Gate RG-5 (FINAL):**
- Full verification suite green on a clean checkout.
- Lint baseline reviewed and reduced.
- A release build signs with the **new** (Phase 0) key only and installs cleanly.
- Maintainer sign-off; archive this plan.

---

## 4. Backlog Not Scheduled Above (intentionally deferred)

These are real but low-impact / cosmetic; batch opportunistically:
- God-class refactor (CQ-006): FileExplorerFragment (2100), Rclone (1745), VirtualContentProvider (1677), RcloneRcd (1114), MainActivity (1006). Only tackle when the file is already open for another change.
- Namespace unification `ca.pkay` → `de.schuelken` (RC-29/CQ-005): high churn, low immediate value; defer until a critical mass of new code justifies it. Decide the boundary first (write an ADR).
- Three overlapping notification abstractions (CQ-007): consolidate during the next notification-feature change.
- SEC-006 AppCenter dead UUID in BuildConfig: harmless; remove the dead `CrashLogger` + `CLI` field opportunistically.
- SEC-007 nanohttpd 2.3.1: monitor for safdav exposure changes; replace only if safdav starts serving real filesystems.

---

## 5. Parallelization Map (at a glance)

```
PHASE 0  ──[serial, blocking]──► RG-0
                                     │
PHASE 1  ┌─ 1A crashes ──┐
         ├─ 1B security ─┼──► RG-1
         └─ 1C build/CI ─┘
                                     │
PHASE 2  ┌─ 2A WorkManager ─┐
         ├─ 2B RcdService ──┤
         ├─ 2C resources ───┼──► RG-2
         ├─ 2D update flow ─┤
         ├─ 2E TODO-bugs ───┤
         └─ 2F cred design ─┘
                                     │
PHASE 3  ┌─ 3A theme/color ─┐
         ├─ 3B strings/i18n ┤
         ├─ 3C a11y/layouts ┼──► RG-3
         └─ 3D UX code ─────┘
                                     │
PHASE 4  ┌─ 4A RemoteItem ──┐
         ├─ 4B dead code ───┤
         ├─ 4C deps ────────┼──► RG-4
         └─ 4D AsyncTask ───┘   (XL — may span multiple PRs)
                                     │
PHASE 5  ┌─ 5A CI hardening ┐
         ├─ 5B docs ────────┼──► RG-5 (FINAL)
         └─ 5C UI polish ───┘
```

**Critical path:** Phase 0 → 1C → 2A/2F → gates → 5A. Everything else can fan out to contributors once its parent gate clears, but release candidates must not proceed while signing, credential-storage, or update-installation gates are unresolved.

---

## 6. How to Resume Implementation

This file is a plan only. To execute, pick a phase/track and start a branch; the file/line references in §1 and the per-track item lists are sufficient context for each track to be handed to an independent agent or contributor. Do not skip review gates.
