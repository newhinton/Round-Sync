@echo off
chcp 65001 >nul
echo ========================================
echo Download Pre-built rclone
echo ========================================
echo.

REM Check if PowerShell is available
where powershell >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: PowerShell not found. Please install PowerShell 5.1+.
    pause
    exit /b 1
)

REM Run PowerShell script
powershell -NoProfile -ExecutionPolicy Bypass -File download-rclone.ps1

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Download failed with error code %ERRORLEVEL%
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo Download completed successfully!
pause
