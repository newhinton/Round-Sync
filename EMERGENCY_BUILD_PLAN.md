# Emergency Build Plan - Bypass rclone Compilation

## Current Status

- **Windows build**: Fails with "could not import crypto/hmac"
- **GitHub Actions**: Fails with same error
- **Go version**: User has 1.25.6, required 1.19.8
- **Root cause**: Go cannot access standard library when cross-compiling to android/arm

## Immediate Solution: Build APK Without rclone

We can build the Android APK without compiling rclone from source.

### Step 1: Use Pre-built rclone Binary

Download official rclone ARM64 binary:
```bash
# Visit https://github.com/rclone/rclone/releases
# Download: rclone-v1.73.1-linux-arm64.zip
# Extract and rename to: librclone.so
```

### Step 2: Place Binary in Correct Location

```bash
# Create directory
mkdir -p app\src\main\jniLibs\arm64-v8a

# Copy binary
copy librclone.so app\src\main\jniLibs\arm64-v8a\
```

### Step 3: Modify build.gradle to Skip rclone Build

Edit `rclone/build.gradle`:

```gradle
task buildArm64(dependsOn: patchRclone) {
    // Skip rclone build for now
    doLast {
        println "Skipping rclone build - using pre-built binary"
    }
}
```

Or better, disable the rclone module entirely:

### Step 4: Update app/build.gradle to Use Pre-built Binary

The app expects `librclone.so` to be in:
```
app/src/main/jniLibs/arm64-v8a/librclone.so
```

### Step 5: Build APK

```bash
# Build APK without rclone compilation
.\gradlew.bat :app:assembleOssDebugArm64V8a
```

## Session Guardian Integration

Once we have a working APK with pre-built rclone, we can add Session Guardian functionality through a different approach:

### Option A: Kotlin Implementation
Move Session Guardian logic from Go to Kotlin/Java layer:
- TOTP generation: Use Java's TOTP libraries
- Backoff logic: Implement in WorkManager
- Health checks: Already in Kotlin (SessionGuardianWorker.kt)
- Re-auth UI: Already in Kotlin

### Option B: Separate rclone Build
- Build rclone separately on Linux machine
- Copy binary to APK build directory
- Sign and package APK

### Option C: Go Plugin
- Create Go binary as separate module
- Load dynamically at runtime
- Bypass build-time linking issues

## Modified Build Script

Create a new script that:
1. Downloads pre-built rclone binary
2. Places it in correct location
3. Builds APK without compiling rclone

```powershell
# download-rclone.ps1
$version = "1.73.1"
$url = "https://github.com/rclone/rclone/releases/download/v${version}/rclone-v${version}-linux-arm64.zip"
$output = "rclone.zip"

Write-Host "Downloading rclone v${version}..." -ForegroundColor Yellow
Invoke-WebRequest -Uri $url -OutFile $output

Write-Host "Extracting..." -ForegroundColor Yellow
Expand-Archive $output -DestinationPath .

Write-Host "Installing to app/src/main/jniLibs/arm64-v8a/..." -ForegroundColor Yellow
New-Item -ItemType Directory -Force -Path "app\src\main\jniLibs\arm64-v8a"
Move-Item rclone "app\src\main\jniLibs\arm64-v8a\librclone.so"

Write-Host "Done!" -ForegroundColor Green
```

## Recommendation

**For now**: Build APK without Session Guardian Go code
1. Download pre-built rclone binary
2. Build APK
3. Install and test basic functionality

**Later**: Add Session Guardian via Kotlin implementation
1. Implement TOTP in Java/Kotlin
2. Implement backoff in WorkManager
3. Integrate with existing UI

This allows us to:
- Have a working APK now
- Test basic rclone functionality
- Add Session Guardian features incrementally
