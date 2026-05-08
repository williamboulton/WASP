@echo off
setlocal

set "ROOT_DIR=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT_DIR%docker\start-metrics-loop.ps1"
