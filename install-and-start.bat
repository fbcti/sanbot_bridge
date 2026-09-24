@echo off
setlocal

set "DEVICE=%~1"
if "%DEVICE%"=="" set "DEVICE=10.30.12.111:5555"

set "APK=%~dp0app\build\outputs\apk\robot\release\Sanbot Bridge-1.0.001-release.apk"
REM set "APK=%~dp0app\build\outputs\apk\robot\debug\Sanbot Bridge-1.0.001-debug.apk"
set "PACKAGE=com.fbcti.sanbot.bridge"
set "ACTIVITY=com.fbcti.sanbot.bridge/.app.BridgeMainActivity"

if not exist "%APK%" (
    echo APK not found: "%APK%"
    echo Build it first with: gradlew.bat assembleRobotRelease
    exit /b 1
)

echo Connecting to %DEVICE%...
adb connect %DEVICE%
if errorlevel 1 exit /b 1

echo Installing "%APK%"...
adb -s %DEVICE% install -r "%APK%"
if errorlevel 1 exit /b 1

echo Starting %PACKAGE%...
adb -s %DEVICE% shell am start -n %ACTIVITY%
if errorlevel 1 exit /b 1

echo Done.
endlocal