@echo off
setlocal
cd /d "%~dp0"

where javac >nul 2>&1
if errorlevel 1 (
    echo Loi: chua cai JDK hoac javac chua co trong PATH.
    pause
    exit /b 1
)

if not exist "build" mkdir "build"

javac -d "build" timeclient.java
if errorlevel 1 (
    echo Bien dich client that bai.
    pause
    exit /b 1
)

if "%~1"=="" (
    echo Mo hop thoai nhap IP server...
    java -cp "build" timeclient
    if errorlevel 1 pause
    exit /b
)

set "SERVER_IP=%~1"

echo Ket noi toi %SERVER_IP%:7000...
java -cp "build" timeclient "%SERVER_IP%"

if errorlevel 1 pause
endlocal
