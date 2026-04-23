# GitHub Actions Build Trigger Script

## Option 1: Use GitHub Actions (RECOMMENDED)
The repository already has a working GitHub Actions workflow (.github/workflows/android.yml) that:
- Runs on Ubuntu Linux (proper build environment)
- Uses JDK 17, Go 1.25.6, Android SDK/NDK
- Builds APKs for all architectures (arm, arm64, x86, x64, universal)
- Uploads artifacts to GitHub

### How to Build via GitHub Actions:
```bash
# Trigger the workflow
gh workflow run android.yml -f

# Or push a trigger commit
git commit --allow-empty -m "Trigger build"
git push origin master

# Or use the web interface:
# Visit: https://github.com/thies2005/CloudBridge/actions
# Click "Run workflow" on the "android-ci" workflow
```

### How to Download the APK:
After the build completes (~5-10 minutes), download from Actions artifacts:
```bash
# List recent workflow runs
gh run list --workflow=android.yml

# Download latest build
gh run download <run-id>
```

## Option 2: Local Build with MinGW (Advanced)

Install MinGW-w64 to provide C compiler for CGO on Windows:

```bash
# Using Chocolatey
choco install mingw

# Using Scoop
scoop install mingw

# Using manual download
# Download from: https://www.mingw-w64.org/

# Then build
./gradlew assembleDebug
```

## Option 3: Skip rclone Build (Workaround)

Modify gradle.properties to skip native rclone compilation:
```gradle
# Add this line to gradle.properties
usePrebuiltRclone=true
```

Then modify rclone/build.gradle to use pre-built binary instead of compiling.

## Option 4: Docker Build (Cross-Platform)

Build in Docker with Windows SDK and NDK:
```bash
docker run -it --rm -v ${PWD}:/workspace -w /tmp \
  -e ANDROID_HOME=/opt/android-sdk \
  -e ANDROID_NDK_HOME=/opt/android-sdk/ndk/29.0.14206865 \
  ghcr.io/android-actions/sdk:latest \
  ./gradlew assembleDebug
```

---

## Current Status

✅ Session Guardian code: Pushed to GitHub (ready for build)
✅ Windows build fixes: Pushed to GitHub
✅ GitHub Actions workflow: Exists and working

## Recommended Next Steps

1. **Use Option 1** (GitHub Actions) - Easiest and most reliable
2. Download APK from GitHub Actions artifacts when complete
3. The APK will work on your Pixel 9 (arm64-v8a)

## Files to Download After Build

Once GitHub Actions completes, download:
- `app/build/outputs/apk/oss/debug/*-oss-arm64-v8a-debug.apk` ← For your Pixel 9
- Other architectures are also available if needed

---

**To trigger a GitHub Actions build now, run:**
```bash
gh workflow run android.yml -f
```

**Or visit:** https://github.com/thies2005/CloudBridge/actions
