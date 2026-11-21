@echo off
setlocal
chcp 65001 >nul
set SCRIPT_DIR=%~dp0
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%provision_tablet.ps1" %*
set CODE=%ERRORLEVEL%
if not "%CODE%"=="0" (
  echo.
  echo Скрипт остановился с ошибкой. Текст выше и лог tools\provision-*.log можно передать разработчику.
  pause
)
exit /b %CODE%
