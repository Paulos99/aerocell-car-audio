@echo off
setlocal
set SCRIPT_DIR=%~dp0
python "%SCRIPT_DIR%publish_content.py" %*
