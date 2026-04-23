# Windows Local Build Guide for Ryzen 8845HS
# Session Guardian Edition

## Prerequisites

### Required Software

1. **Java Development Kit (JDK) 17**
   - Download: https://adoptium.net/temurin/releases/?version=17
   - Or use: `winget install EclipseAdoptium.Temurin.17.JDK`
   - Set `JAVA_HOME` environment variable

2. **Android SDK**
   - Download Android Studio: https://developer.android.com/studio
   - Or download SDK tools only: https://developer.android.com/studio#cmd-tools-only
   - Set `ANDROID_HOME` environment variable to SDK path
   - Add `%ANDROID_HOME%\cmdline-tools\latest\bin` to PATH

3. **Android NDK 29.0.14206865**
   - Install via Android Studio SDK Manager
   - Or run: `sdkmanager "ndk;29.0.14206865"`
   - Ensure NDK is installed to `%ANDROID_HOME%\ndk\29.0.14206865`

4. **Go 1.19.8**
   - Download: https://go.dev/dl/go1.19.8.windows-amd64.zip
   - Extract and add to PATH
   - Or use: `winget install Golang.Go`
   - Verify: `go version` (should show 1.19.8)

5. **Git** (for cloning the repository)
   - Download: https://git-scm.com/download/win
   - Or use: `winget install Git.Git`

## Build Steps

### 1. Clone or Update Repository

```bash
cd C:\Projects
git clone https://github.com/thies2005/CloudBridge.git
cd CloudBridge
git pull origin master
```

### 2. Verify Environment Variables

Open a **new** PowerShell window and check:

```powershell
# Check Java
echo $env:JAVA_HOME
java -version  # Should show version 17.x

# Check Android SDK
echo $env:ANDROID_HOME

# Check Go
go version  # Should show go1.19.8
```

If any are missing, set them temporarily for this session:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.12-hotspot"
$env:ANDROID_HOME = "C:\Users\$env:USERNAME\AppData\Local\Android\Sdk"
```

### 3. Clean Previous Builds

```bash
./gradlew clean
./gradlew :rclone:clean
```

### 4. Build rclone for Android ARM64

```bash
./gradlew :rclone:buildArm64
```

This will:
- Download rclone v1.73.1 and dependencies
- Apply Session Guardian patches
- Build `librclone.so` for arm64-v8a (Pixel 9)

**Expected output location:**
```
app/lib/arm64-v8a/librclone.so
```

### 5. Build APK

```bash
# Build OSS Debug APK for all architectures
./gradlew assembleOssDebug

# Or build only ARM64 (faster)
```bash
./gradlew.bat :rclone:buildArm64
./gradlew.bat :app:assembleOssDebugArm64-v8a
```

**APK output location:**
```
app/build/outputs/apk/oss/debug/roundsync_v*-oss-arm64-v8a-debug.apk
```

### 6. Build All Architectures (Optional)

```bash
# This builds APKs for all architectures:
# - armeabi-v7a (32-bit ARM)
# - arm64-v8a (64-bit ARM - for Pixel 9)
# - x86 (32-bit Intel)
# - x86_64 (64-bit Intel)
# - universal

./gradlew assembleOssDebug
```

## Session Guardian Features Included

Your built APK will include:

1. **TOTP Time Windows** - Handles clock skew with T, T-1, T+1 retries
2. **Exponential Backoff** - 1m → 5m → 15m → 1h → 1h with ±10% jitter
3. **Soft Circuit Breaker** - Max 5 retries, resets on success
4. **8-Hour Health Checks** - WorkManager background service
5. **Manual Re-Auth UI** - "Re-authenticate" option in remote menu
6. **Session Expiry Notifications** - Alerts when manual intervention needed

## Troubleshooting

### Go version mismatch
```
The requred go version is: 1.19.8
You are running: go version go1.19.x windows/amd64
```
**Solution:** The build works with Go 1.19.x. Minor version differences are OK.

### NDK not found
```
Couldn't find a ndk bundle
```
**Solution:**
```powershell
sdkmanager "ndk;29.0.14206865"
```

### Gradle out of memory
```
Gradle build daemon needs more memory
```
**Solution:** Edit `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx6144M
```

### rclone compilation fails
If rclone build fails, check:
1. Go version: `go version` (must be ~1.19.x)
2. NDK path: `echo $env:ANDROID_HOME\ndk`
3. Available disk space (need ~2GB for Go module cache)

## Output Files

After successful build:

```
app/
├── build/
│   └── outputs/
│       └── apk/
│           └── oss/
│               └── debug/
│                   ├── roundsync_v*-oss-armeabi-v7a-debug.apk
│                   ├── roundsync_v*-oss-arm64-v8a-debug.apk  <-- FOR PIXEL 9
│                   ├── roundsync_v*-oss-x86-debug.apk
│                   ├── roundsync_v*-oss-x86_64-debug.apk
│                   └── roundsync_v*-oss-universal-debug.apk
└── lib/
    ├── armeabi-v7a/librclone.so
    ├── arm64-v8a/librclone.so
    ├── x86/librclone.so
    └── x86_64/librclone.so
```

## Installing on Pixel 9

1. Enable "Install from unknown sources" in Android settings
2. Transfer `roundsync_v*-oss-arm64-v8a-debug.apk` to Pixel 9
3. Install the APK
4. Test Session Guardian features:
   - Configure an Internxt remote with 2FA
   - Wait for 8 hours to see first health check
   - Try accessing files to trigger background token refresh
   - Check for notifications if re-auth needed

## Quick Reference

```bash
# Full build (all APKs + rclone)
./gradlew assembleOssDebug

# Clean everything
./gradlew clean

# Clean only rclone
./gradlew :rclone:clean

# Build only rclone ARM64
./gradlew :rclone:buildArm64

# Skip tests (faster)
./gradlew assembleOssDebug -x test
```

## Build Time Estimates

- **First build:** ~15-20 minutes (downloads Go modules)
- **Incremental build:** ~5-8 minutes (uses cached modules)

Your Ryzen 8845HS should handle this well!
