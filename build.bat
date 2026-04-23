@echo off
chcp 65001 >nul
echo ========================================
echo CloudBridge Build Script
echo Session Guardian Edition
echo ========================================
echo.

REM Check if PowerShell is available
where powershell >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: PowerShell not found. Please install PowerShell 5.1+.
    pause
    exit /b 1
)

REM Run the PowerShell script
powershell -NoProfile -ExecutionPolicy Bypass -File build-windows.ps1

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Build failed with error code %ERRORLEVEL%
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo Build completed successfully!
pause
