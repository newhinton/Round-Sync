# Windows Build Script for Session Guardian
# Run from CloudBridge directory in PowerShell

Write-Host "=======================================" -ForegroundColor Cyan
Write-Host "CloudBridge Build Script" -ForegroundColor Cyan
Write-Host "Session Guardian Edition" -ForegroundColor Cyan
Write-Host "=======================================" -ForegroundColor Cyan
Write-Host ""

# Check if we're in the right directory
if (-not (Test-Path ".\gradlew.bat")) {
    Write-Host "ERROR: gradlew.bat not found. Run this script from CloudBridge directory." -ForegroundColor Red
    exit 1
}

Write-Host "[1/6] Checking environment..." -ForegroundColor Yellow
Write-Host "  Note: Go version check is informational only." -ForegroundColor Cyan
Write-Host "  Build works with Go 1.19.x through 1.25.x" -ForegroundColor Cyan

# Check Go
$goVersion = go version 2>&1
Write-Host "  Go: $goVersion" -ForegroundColor White

# Check Java
$javaVersion = java -version 2>&1
Write-Host "  Java: $javaVersion" -ForegroundColor White

# Check Android SDK
if ($env:ANDROID_HOME) {
    Write-Host "  ANDROID_HOME: $($env:ANDROID_HOME)" -ForegroundColor White
} else {
    Write-Host "  WARNING: ANDROID_HOME not set" -ForegroundColor Yellow
}

# Check NDK
$ndkPath = "$env:ANDROID_HOME\ndk\29.0.14206865"
if (Test-Path $ndkPath) {
    Write-Host "  NDK: Found" -ForegroundColor Green
} else {
    Write-Host "  WARNING: NDK 29.0.14206865 not found" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "[2/6] Select build target:" -ForegroundColor Yellow
Write-Host "  1 - Full build (all APKs)" -ForegroundColor White
Write-Host "  2 - ARM64 only (for Pixel 9, faster)" -ForegroundColor White
Write-Host "  3 - Use pre-built rclone (download, no compilation)" -ForegroundColor Cyan
Write-Host "  4 - Clean build (delete caches)" -ForegroundColor White
Write-Host "  5 - Exit" -ForegroundColor White

$choice = Read-Host "Enter choice (1-5):"

Write-Host ""

switch ($choice) {
    "1" {
        Write-Host "[3/6] Starting full build (all architectures)..." -ForegroundColor Yellow
        Write-Host "This will take 15-20 minutes on first build." -ForegroundColor Cyan

        Write-Host ""
        Write-Host "[4/6] Cleaning previous builds..." -ForegroundColor Yellow
        .\gradlew.bat clean

        Write-Host ""
        Write-Host "[5/6] Building rclone for all architectures..." -ForegroundColor Yellow
        .\gradlew.bat :rclone:buildAll

        Write-Host ""
        Write-Host "[6/6] Building APKs for all architectures..." -ForegroundColor Yellow
        .\gradlew.bat assembleOssDebug

        Write-Host ""
        Write-Host "=======================================" -ForegroundColor Green
        Write-Host "BUILD COMPLETE!" -ForegroundColor Green
        Write-Host "=======================================" -ForegroundColor Green
        Write-Host ""
        Write-Host "APKs are in: app\build\outputs\apk\oss\debug\" -ForegroundColor White
        Write-Host ""
        Write-Host "For Pixel 9, install:" -ForegroundColor Cyan
        Write-Host "  roundsync_v*-oss-arm64-v8a-debug.apk" -ForegroundColor White
    }

    "2" {
        Write-Host "[3/6] Starting ARM64 build only (for Pixel 9)..." -ForegroundColor Yellow
        Write-Host "WARNING: This may fail due to Go cross-compilation issues." -ForegroundColor Red
        Write-Host "RECOMMENDED: Use option 3 (pre-built rclone) instead." -ForegroundColor Cyan
        Write-Host ""

        $confirm = Read-Host "Continue anyway? (y/n)"
        if ($confirm -ne "y" -and $confirm -ne "Y") {
            Write-Host ""
            Write-Host "Build cancelled. Please use option 3 instead." -ForegroundColor Yellow
            exit 0
        }

        Write-Host ""
        Write-Host "[4/6] Cleaning previous builds..." -ForegroundColor Yellow
        .\gradlew.bat clean

        Write-Host ""
        Write-Host "[5/6] Building rclone for ARM64..." -ForegroundColor Yellow
        .\gradlew.bat :rclone:buildArm64

        if ($LASTEXITCODE -ne 0) {
            Write-Host ""
            Write-Host "=======================================" -ForegroundColor Red
            Write-Host "rclone BUILD FAILED!" -ForegroundColor Red
            Write-Host "=======================================" -ForegroundColor Red
            Write-Host ""
            Write-Host "As expected, Go cross-compilation to android/arm fails on Windows." -ForegroundColor Yellow
            Write-Host ""
            Write-Host "SOLUTION: Use option 3 to download pre-built rclone." -ForegroundColor Cyan
            Write-Host ""
            exit 1
        }

        Write-Host ""
        Write-Host "[6/6] Building APK for ARM64..." -ForegroundColor Yellow
        .\gradlew.bat :app:assembleOssDebugArm64V8a

        if ($LASTEXITCODE -eq 0) {
            Write-Host ""
            Write-Host "=======================================" -ForegroundColor Green
            Write-Host "BUILD COMPLETE!" -ForegroundColor Green
            Write-Host "=======================================" -ForegroundColor Green
            Write-Host ""
            Write-Host "APK for Pixel 9:" -ForegroundColor Cyan

            $apkFiles = Get-ChildItem ".\app\build\outputs\apk\oss\debug\*arm64-v8a*.apk" -ErrorAction SilentlyContinue
            if ($apkFiles) {
                $apkFiles | ForEach-Object {
                    Write-Host "  $($_.Name)" -ForegroundColor White
                }
            } else {
                Write-Host "  WARNING: No APK files found!" -ForegroundColor Yellow
            }
        } else {
            Write-Host ""
            Write-Host "=======================================" -ForegroundColor Red
            Write-Host "BUILD FAILED!" -ForegroundColor Red
            Write-Host "=======================================" -ForegroundColor Red
            Write-Host ""
            Write-Host "Please check the error messages above." -ForegroundColor Yellow
        }
    }

    "3" {
        Write-Host "[3/6] Using pre-built rclone binary..." -ForegroundColor Yellow
        Write-Host "This is the recommended method for Windows builds!" -ForegroundColor Cyan
        Write-Host ""

        # Check if already downloaded
        if (Test-Path "app\src\main\jniLibs\arm64-v8a\librclone.so") {
            Write-Host "Pre-built rclone already exists." -ForegroundColor Green
            $useExisting = Read-Host "  Re-download? (y/n) [default: n]"
            if ($useExisting -ne "y" -and $useExisting -ne "Y") {
                Write-Host ""
                Write-Host "[4/6] Building APK with existing binary..." -ForegroundColor Yellow
            } else {
                Write-Host ""
                Write-Host "[4/6] Downloading new rclone binary..." -ForegroundColor Yellow
                & .\download.bat
                if ($LASTEXITCODE -ne 0) {
                    exit 1
                }
            }
        } else {
            Write-Host ""
            Write-Host "[4/6] Downloading rclone binary..." -ForegroundColor Yellow
            & .\download.bat
            if ($LASTEXITCODE -ne 0) {
                exit 1
            }
        }

        Write-Host ""
        Write-Host "[5/6] Building APK for ARM64..." -ForegroundColor Yellow
        .\gradlew.bat :app:assembleOssDebugArm64V8a

        if ($LASTEXITCODE -eq 0) {
            Write-Host ""
            Write-Host "=======================================" -ForegroundColor Green
            Write-Host "BUILD COMPLETE!" -ForegroundColor Green
            Write-Host "=======================================" -ForegroundColor Green
            Write-Host ""
            Write-Host "APK for Pixel 9:" -ForegroundColor Cyan

            $apkFiles = Get-ChildItem ".\app\build\outputs\apk\oss\debug\*arm64-v8a*.apk" -ErrorAction SilentlyContinue
            if ($apkFiles) {
                $apkFiles | ForEach-Object {
                    Write-Host "  $($_.Name)" -ForegroundColor White
                }
            } else {
                Write-Host "  WARNING: No APK files found!" -ForegroundColor Yellow
            }
        } else {
            Write-Host ""
            Write-Host "=======================================" -ForegroundColor Red
            Write-Host "BUILD FAILED!" -ForegroundColor Red
            Write-Host "=======================================" -ForegroundColor Red
            Write-Host ""
            Write-Host "Please check error messages above." -ForegroundColor Yellow
        }
    }

    "4" {
        Write-Host "[3/6] Cleaning all caches..." -ForegroundColor Yellow
        .\gradlew.bat clean
        .\gradlew.bat :rclone:clean

        Write-Host ""
        Write-Host "=======================================" -ForegroundColor Green
        Write-Host "CLEAN COMPLETE!" -ForegroundColor Green
        Write-Host "=======================================" -ForegroundColor Green
        Write-Host ""
        Write-Host "Go module cache (rclone/cache) has been cleared." -ForegroundColor White
    }

    "5" {
        Write-Host "Exiting..." -ForegroundColor Yellow
        exit 0
    }

    default {
        Write-Host "Invalid choice. Exiting." -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "Press any key to continue..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
