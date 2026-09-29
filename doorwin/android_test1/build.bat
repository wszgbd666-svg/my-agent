@echo off
setlocal
cd /d "%~dp0"
set JDK=D:\AI_agent\.apktools\jdk\jdk-17.0.20.1+1
set BT=D:\AI_agent\.apktools\bt\android-14
set ANDROID_JAR=D:\AI_agent\.apktools\platform\android-34\android.jar
set PATH=%JDK%\bin;%PATH%

echo [1/6] Compiling Java...
if exist build rmdir /s /q build
mkdir build\classes
"%JDK%\bin\javac.exe" -source 1.8 -target 1.8 -bootclasspath "%ANDROID_JAR%" -encoding UTF-8 -d build\classes java\com\doorwin\app\MainActivity.java
if errorlevel 1 goto :err

echo [2/6] Building dex...
call "%BT%\d8.bat" --release --lib "%ANDROID_JAR%" --output build build\classes\com\doorwin\app\MainActivity.class
if errorlevel 1 goto :err

echo [3/6] Packaging resources and assets...
mkdir build\res-compiled
"%BT%\aapt2.exe" compile --dir res -o build\res-compiled\res.zip
if errorlevel 1 goto :err
"%BT%\aapt2.exe" link -o build\base.apk -I "%ANDROID_JAR%" --manifest AndroidManifest.xml -A assets build\res-compiled\res.zip --auto-add-overlay
if errorlevel 1 goto :err

echo [4/6] Fixing paths, adding dex, aligning...
python fixzip.py
if errorlevel 1 goto :err
"%BT%\zipalign.exe" -f 4 build\fixed.apk build\aligned.apk

echo [5/6] Creating signing key (first time only)...
if not exist doorwin.keystore "%JDK%\bin\keytool.exe" -genkeypair -keystore doorwin.keystore -alias doorwin -keyalg RSA -keysize 2048 -validity 10950 -storepass doorwin123 -keypass doorwin123 -dname "CN=DoorWin,O=DoorWin,C=CN"

echo [6/6] Signing...
"%BT%\apksigner.bat" sign --ks doorwin.keystore --ks-pass pass:doorwin123 --key-pass pass:doorwin123 --out doorwin.apk build\aligned.apk
if errorlevel 1 goto :err
"%BT%\apksigner.bat" verify doorwin.apk
echo.
echo BUILD OK: %cd%\doorwin.apk
goto :eof

:err
echo BUILD FAILED
exit /b 1
