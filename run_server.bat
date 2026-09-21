@echo off
setlocal
cd /d "%~dp0"
chcp 65001 >nul

where javac >nul 2>&1
if errorlevel 1 (
    echo Loi: chua cai JDK hoac javac chua co trong PATH.
    pause
    exit /b 1
)

if not exist "build" mkdir "build"

javac -encoding UTF-8 -d "build" timeserver.java
if errorlevel 1 (
    echo Bien dich server that bai.
    pause
    exit /b 1
)

java -cp "build" timeserver
set "SERVER_EXIT_CODE=%ERRORLEVEL%"

echo.
if not "%SERVER_EXIT_CODE%"=="0" (
    echo Server da dung voi ma loi %SERVER_EXIT_CODE%.
) else (
    echo Server da dung.
)
echo Nhan phim bat ky de dong cua so nay...
pause >nul
endlocal & exit /b %SERVER_EXIT_CODE%
