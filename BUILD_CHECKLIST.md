# Windows Build Checklist - Ryzen 8845HS

## Environment Verification

Before building, verify each item below:

- [ ] **Java 17+ installed**
  - Run: `java -version`
  - Expected: `version 17.x.x`
  - Download: https://adoptium.net/temurin/releases/?version=17

- [ ] **ANDROID_HOME set**
  - Run: `echo %ANDROID_HOME%`
  - Expected: Path to Android SDK (e.g., `C:\Users\YourName\AppData\Local\Android\Sdk`)
  - If not set: Set via System Properties > Environment Variables

- [ ] **Android NDK 29.0.14206865 installed**
  - Run: `dir "%ANDROID_HOME%\ndk\29.0.14206865"`
  - Expected: Directory exists with toolchains folder
  - If not: Run `sdkmanager "ndk;29.0.14206865"` in Android Studio

- [ ] **Go 1.19.8 installed**
  - Run: `go version`
  - Expected: `go version go1.19.8 windows/amd64`
  - Download: https://go.dev/dl/go1.19.8.windows-amd64.zip

- [ ] **Git installed**
  - Run: `git --version`
  - Expected: `git version 2.x.x`
  - Download: https://git-scm.com/download/win

- [ ] **Repository cloned**
  - Navigate to: `C:\Projects\CloudBridge` (or your preferred location)
  - Run: `git pull` if already cloned
  - Or: `git clone https://github.com/thies2005/CloudBridge.git`

## Quick Environment Test

Open a **new** Command Prompt or PowerShell and run:

```batch
cd C:\Projects\CloudBridge
.\gradlew.bat --version
```

Expected output:
```
Gradle 8.x.x
------------------------------------------------------------
Gradle 8.x.x
------------------------------------------------------------
Build time:   ...
Kotlin:       1.x.x
...
```

## Build Methods

### Method 1: Automated Script (Recommended)

Double-click `build.bat` in CloudBridge directory.

This provides:
1. Environment verification
2. Build menu (full, ARM64-only, clean)
3. Progress indicators
4. Success/failure messages

### Method 2: Manual Command Line

```batch
REM Full build (all architectures, first build = 15-20 min)
.\gradlew.bat assembleOssDebug

REM ARM64 only for Pixel 9 (faster = 5-8 min)
.\gradlew.bat :rclone:buildArm64
.\gradlew.bat :app:assembleOssDebugArm64-v8a

REM Clean caches
.\gradlew.bat clean
.\gradlew.bat :rclone:clean
```

### Method 3: Using Android Studio

1. Open Android Studio
2. File > Open > Navigate to CloudBridge directory
3. Wait for Gradle sync
4. Build > Build Bundle(s) / APK(s) > Build APK(s)
5. Select debug variant

## Expected Build Output

```
app/
└── build/
    └── outputs/
        └── apk/
            └── oss/
                └── debug/
                    ├── roundsync_v*-oss-armeabi-v7a-debug.apk    (32-bit ARM devices)
                    ├── roundsync_v*-oss-arm64-v8a-debug.apk     (Pixel 9 - 64-bit ARM)
                    ├── roundsync_v*-oss-x86-debug.apk              (32-bit Intel emulators)
                    ├── roundsync_v*-oss-x86_64-debug.apk           (64-bit Intel emulators)
                    └── roundsync_v*-oss-universal-debug.apk       (All devices)
```

## Session Guardian Features Built In

When you install the APK, you'll have:

✅ **TOTP Time Windows**
   - Automatically retries with T, T-1, T+1 time offsets
   - Handles device clock drift up to ±30 seconds

✅ **Exponential Backoff**
   - 1st failure: Wait 1 minute
   - 2nd failure: Wait 5 minutes
   - 3rd failure: Wait 15 minutes
   - 4th+ failure: Wait 1 hour
   - All with ±10% random jitter

✅ **Soft Circuit Breaker**
   - Max 5 retry attempts before requiring manual re-auth
   - Resets to 0 after successful operation
   - Returns "auth exceeded max retries: manual re-auth required"

✅ **8-Hour Health Checks**
   - WorkManager runs `rclone lsd remote:` every 8 hours
   - Silently refreshes tokens in background
   - Only requires battery and network constraints

✅ **Manual Re-Auth UI**
   - Long-press on remote → "Re-authenticate"
   - Opens RemoteConfig activity for token refresh

✅ **Session Expiry Notifications**
   - Shows notification: "CloudBridge: Session for [Remote] expired"
   - Taps open MainActivity to remote list
   - Direct access to re-auth menu

## Transfer to Pixel 9

1. **Enable sideloading** (if not enabled):
   - Settings > Security > Install unknown apps
   - Allow from this source

2. **Transfer APK**:
   - USB cable: Copy `roundsync_v*-oss-arm64-v8a-debug.apk` to Pixel 9
   - Cloud upload: Upload APK to Google Drive, download on Pixel 9
   - ADB: `adb install roundsync_v*-oss-arm64-v8a-debug.apk`

3. **Install**:
   - Tap APK file
   - Install

4. **Test Session Guardian**:
   - Add an Internxt remote with 2FA enabled
   - Access files to trigger token refresh
   - Wait ~8 hours to see first health check
   - Monitor notifications

## Troubleshooting

### Issue: "gradlew.bat not recognized"
```
Solution: Run from CloudBridge directory or use full path:
C:\Projects\CloudBridge\gradlew.bat
```

### Issue: "Could not determine java version"
```
Solution: Set JAVA_HOME environment variable:
1. Windows Key + R > "env" (Edit system environment variables)
2. New variable: JAVA_HOME
3. Value: C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot
4. Restart Command Prompt
```

### Issue: "SDK location not found"
```
Solution: Set ANDROID_HOME:
1. Windows Key + R > "env"
2. New variable: ANDROID_HOME
3. Value: C:\Users\YourName\AppData\Local\Android\Sdk
4. Restart Command Prompt
```

### Issue: "Unsupported host OS or architecture"
```
Solution: This is a Ryzen 8845HS (AMD64), so it should work.
Check: `echo %PROCESSOR_ARCHITECTURE%`
Expected: AMD64
```

### Issue: Build is very slow
```
Solution:
- First build always slower (downloads Go modules)
- Second build should be 5-8 minutes
- Check your internet speed
- Check disk space (need ~2GB for cache)
```

## Performance Expectations

On your **Ryzen 8845HS**:

| Build Type | First Time | Incremental |
|------------|-------------|-------------|
| Full build (all APKs) | 15-20 min | 5-8 min |
| ARM64 only (Pixel 9) | 8-12 min | 3-5 min |
| Clean | 1-2 min | 1-2 min |

## Support

If build fails:

1. Check this checklist again
2. Read detailed guide: `WINDOWS_BUILD_GUIDE.md`
3. Check Session Guardian implementation: `SESSION_GUARDIAN_IMPLEMENTATION.md`
4. Review build log: `app/build/reports/`
5. Check GitHub issues: https://github.com/thies2005/CloudBridge/issues
