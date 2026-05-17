@echo off
call "%~dp0env.bat"

REM ================================================
REM 1) JMX 옵션 조건부 적용
REM ================================================
if /I "%ENABLE_JMX%"=="true" (
    set "JMX_EN=true"
    if "%JMX_PORT%"=="" set "JMX_PORT=9999"
) else (
    set "JMX_EN=false"
)

if "%JMX_EN%"=="true" (
    set "JMX_OPTS=-Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=%JMX_PORT% -Dcom.sun.management.jmxremote.local.only=false -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"
) else (
    set "JMX_OPTS="
)

REM ================================================
REM 2) JAVA_OPTS 최종 병합
REM ================================================
REM env.bat에서 세팅된 BASE_JAVA_OPTS에 JMX와 메이븐 옵션 추가
set "JAVA_OPTS=%BASE_JAVA_OPTS% %JMX_OPTS% @java.opts@"

echo JAVA_OPTS set to:
echo %JAVA_OPTS%

REM ================================================
REM 5) 기존 PID 체크 및 실행 로직 그대로 유지
REM ================================================
set "PID_FILE=%BASE_DIR%\bin\%APP_NAME%.pid"

if exist "%PID_FILE%" (
    set /p PID=<"%PID_FILE%"
    REM PID가 실행 중인지 확인
    tasklist /FI "PID eq %PID%" | findstr /I "java.exe" >nul
    if %ERRORLEVEL% == 0 (
        echo %APP_NAME% is already running with PID %PID%.
        goto :exit
    ) else (
        echo PID file found but process %PID% is not running, removing stale PID file.
        del "%PID_FILE%"
    )
)

echo Starting %APP_NAME% application...
"%JAVA_EXEC%" %JAVA_OPTS% -cp "%CLASSPATH%" io.zefio.launcher.ZefioApplication
REM 서비스로 등록 안하고 사용할 시엔 이렇게 사용 start "" "%JAVA_EXEC%" %JAVA_OPTS% -cp "%CLASSPATH%" io.zefio.launcher.ZefioApplication


REM 마지막으로 실행된 java.exe PID 저장
for /f "tokens=2" %%a in ('tasklist /FI "IMAGENAME eq java.exe" /FO LIST ^| findstr /I "PID" ^| sort /R') do (
    echo %%a > "%BASE_DIR%\bin\%APP_NAME%.pid"
    goto :break
)
:break
