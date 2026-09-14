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

javac -d "build" timeserver.java
if errorlevel 1 (
    echo Bien dich server that bai.
    pause
    exit /b 1
)

java -cp "build" timeserver

if errorlevel 1 pause
endlocal
