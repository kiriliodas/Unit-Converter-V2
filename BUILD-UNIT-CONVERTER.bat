@echo off
setlocal
cd /d "%~dp0"
title Unit Converter V2 - Test, Lint, Build and Sign

if not exist "%~dp0Build-UnitConverter.ps1" (
    echo ERROR: Build-UnitConverter.ps1 is missing.
    echo Keep BUILD-UNIT-CONVERTER.bat and Build-UnitConverter.ps1 together
    echo in the project root, beside gradlew.bat.
    echo.
    pause
    exit /b 1
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Build-UnitConverter.ps1" %*
set "BUILD_EXIT=%ERRORLEVEL%"

echo.
if "%BUILD_EXIT%"=="0" (
    echo SUCCESS. The signed APK is in the release-output folder.
) else (
    echo FAILED with exit code %BUILD_EXIT%.
    echo Send the newest complete .log file from the build-logs folder.
    echo Never send the .jks file or any signing password.
)
echo.
echo Press any key to close this window...
pause >nul
exit /b %BUILD_EXIT%
