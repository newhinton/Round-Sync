# Download Pre-built rclone for Android
# Bypasses Go cross-compilation issues

Write-Host "=======================================" -ForegroundColor Cyan
Write-Host "Download Pre-built rclone" -ForegroundColor Cyan
Write-Host "=======================================" -ForegroundColor Cyan
Write-Host ""

$version = "1.73.1"
$url = "https://github.com/rclone/rclone/releases/download/v${version}/rclone-v${version}-linux-arm64.zip"
$output = "rclone.zip"
$targetDir = "app\src\main\jniLibs\arm64-v8a"

Write-Host "[1/4] Downloading rclone v${version}..." -ForegroundColor Yellow
Write-Host "URL: $url" -ForegroundColor White

try {
    # Use TLS 1.2+ and progress
    $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest -Uri $url -OutFile $output -UseBasicParsing

    if (-not (Test-Path $output)) {
        Write-Host "ERROR: Download failed!" -ForegroundColor Red
        exit 1
    }

    Write-Host "  Downloaded: $([math]::Round((Get-Item $output).Length / 1MB, 2)) MB" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Failed to download: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[2/4] Extracting archive..." -ForegroundColor Yellow

try {
    Expand-Archive $output -DestinationPath . -Force
    Write-Host "  Extracted successfully" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Failed to extract: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[3/4] Installing to $targetDir..." -ForegroundColor Yellow

try {
    # Create target directory
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null

    # Move and rename rclone to librclone.so
    $extractedFile = "rclone-v$version-linux-arm64\rclone"
    if (Test-Path $extractedFile) {
        Move-Item -Force $extractedFile "$targetDir\librclone.so"
        Write-Host "  Installed: $targetDir\librclone.so" -ForegroundColor Green
    } elseif (Test-Path "rclone") {
        Move-Item -Force "rclone" "$targetDir\librclone.so"
        Write-Host "  Installed: $targetDir\librclone.so" -ForegroundColor Green
    } else {
        Write-Host "ERROR: rclone binary not found in archive or $extractedFile!" -ForegroundColor Red
        exit 1
    }

    # Verify file
    $soFile = "$targetDir\librclone.so"
    if (Test-Path $soFile) {
        $size = [math]::Round((Get-Item $soFile).Length / 1MB, 2)
        Write-Host "  Size: $size MB" -ForegroundColor White
    }
} catch {
    Write-Host "ERROR: Failed to install: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[4/4] Cleaning up..." -ForegroundColor Yellow
Remove-Item $output -ErrorAction SilentlyContinue
Write-Host "  Removed $output" -ForegroundColor Green

Write-Host ""
Write-Host "=======================================" -ForegroundColor Green
Write-Host "SUCCESS!" -ForegroundColor Green
Write-Host "=======================================" -ForegroundColor Green
Write-Host ""
Write-Host "Pre-built rclone is now installed." -ForegroundColor White
Write-Host "You can now build the APK without compiling rclone." -ForegroundColor White
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  1. Build APK: ./gradlew.bat :app:assembleOssDebug" -ForegroundColor White
Write-Host "  2. APK will be in: app\build\outputs\apk\oss\debug\" -ForegroundColor White
Write-Host ""
Write-Host "Press any key to continue..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
