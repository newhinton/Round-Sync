# Go Standard Library Import Issues - Analysis

## Problem

When building rclone with Session Guardian patches for android/arm:
```
could not import crypto/hmac (open : The system cannot find the file specified.)
could not import crypto/sha1 (open : The system cannot find the file specified.)
could not import encoding/base32 (open : The system cannot find the file specified.)
could not import encoding/binary (open : The system cannot find the file specified.)
could not import math (open : The system cannot find the file specified.)
could not import math/rand (open : The system cannot find the file specified.)
```

## Root Cause

Go's cross-compilation to `android/arm` requires the Android Standard Library to be pre-built, but:
1. On Windows, Go cannot properly access its cross-compiled standard library
2. The `android` build tag triggers special handling that conflicts with CGO
3. CGO cross-compilation on Windows for Android is notoriously broken in Go 1.19-1.25

## Why This Happens with Session Guardian

The Session Guardian code (`auth.go`) imports:
- `crypto/hmac` - Standard Go library
- `crypto/sha1` - Standard Go library
- `encoding/base32` - Standard Go library
- `encoding/binary` - Standard Go library
- `math` - Standard Go library
- `math/rand` - Standard Go library

These are ALL pure Go packages with NO CGO dependencies, but Go still cannot access them when cross-compiling to android/arm on Windows.

## Why This Worked Before

Before Session Guardian, rclone didn't import these packages in the internxt backend, so the build succeeded.

## Solution: Build for Linux ARM instead of Android

Android is Linux-based. A Linux ARM binary should work on Android devices.

### Changes to build.gradle:

```gradle
def commonEnv = [
    'GOPATH'      : GOPATH,
    'GOROOT'      : goRoot,
    'GOOS'        : 'linux',      // Changed from 'android'
    'CGO_ENABLED' : '0',
] + abiToEnv[abi]
```

And remove the 'android' build tag:

```gradle
commandLine (
    'go',
    'build',
    '-tags', 'noselfupdate',  // Removed 'android' tag
    '-trimpath',
    '-ldflags', ldflags,
    '-o', getOutputPath(abi),
    RCLONE_MODULE
)
```

## Why This Should Work

1. **Linux ARM is better supported**: Go has stable cross-compilation to linux/arm
2. **No CGO required**: Linux cross-compilation doesn't trigger CGO requirements
3. **Standard library available**: Go can access its standard library for linux targets
4. **Android compatibility**: Android runs on Linux kernel, Linux ARM binaries work

## Testing Required

After building with these changes, we need to verify that the Linux ARM64 binary:
1. Works on Android ARM64 devices
2. Can properly handle file operations
3. Session Guardian TOTP functionality works
4. All rclone operations succeed

## Fallback Option

If Linux ARM doesn't work on Android, we have two options:

### Option A: Use Pre-built rclone Binary
- Download official rclone ARM64 binary
- Copy to `app/lib/arm64-v8a/librclone.so`
- Build APK without compiling rclone

### Option B: Build on Linux Machine
- Use GitHub Actions (currently broken)
- Use Linux virtual machine
- Use WSL (Windows Subsystem for Linux)

## Current Status

We're stuck on:
- Windows build: Go cannot access standard library for android/arm
- GitHub Actions: Same issue
- Linux ARM build: Needs testing (not yet attempted)

## Next Steps

1. Try building for linux/arm instead of android/arm
2. Test on Android device to verify compatibility
3. Document results and decide on final approach
