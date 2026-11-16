@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1" %*
set "plannerExit=%ERRORLEVEL%"
if not "%plannerExit%"=="0" (
    echo.
    echo Campus Planner could not start. See the error above.
    pause
)
exit /b %plannerExit%
