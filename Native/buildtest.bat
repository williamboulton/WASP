@echo off
setlocal EnableDelayedExpansion
REM =============================================================================
REM buildtest.bat — compile and run all tests\cpp\test_*.cpp
REM   Each test exits 0 = PASS, non-zero = FAIL.
REM Run from: x64 Native Tools Command Prompt for VS (or after vcvarsall.bat)
REM =============================================================================

cd /d "%~dp0"

where cl >nul 2>nul
if errorlevel 1 (
    echo ERROR: cl.exe not found. Run this from "x64 Native Tools Command Prompt for VS".
    exit /b 1
)

REM Match build.bat SDK paths so includes resolve if the global env is incomplete
set "INCLUDE=%INCLUDE%;C:\Program Files (x86)\Windows Kits\10\Include\10.0.26100.0\ucrt;C:\Program Files (x86)\Windows Kits\10\Include\10.0.26100.0\um;C:\Program Files (x86)\Windows Kits\10\Include\10.0.26100.0\shared"
set "LIB=%LIB%;C:\Program Files (x86)\Windows Kits\10\Lib\10.0.26100.0\ucrt\x64;C:\Program Files (x86)\Windows Kits\10\Lib\10.0.26100.0\um\x64"

if not exist "build" mkdir build

echo.
echo ========================================
echo C++ unit tests (tests\cpp\test_*.cpp)
echo ========================================
echo.

set FAILCOUNT=0
set TESTCOUNT=0

for %%F in (tests\cpp\test_*.cpp) do (
    set /a TESTCOUNT+=1
    echo [%%~nF] Compiling...
    cl /nologo /EHsc /W4 /std:c++17 /Fo:build\%%~nF.obj /Fe:build\%%~nF.exe "%%~fF"
    if errorlevel 1 (
        echo [%%~nF] COMPILE FAIL
        set /a FAILCOUNT+=1
    ) else (
        echo [%%~nF] Running...
        build\%%~nF.exe
        if errorlevel 1 (
            echo [%%~nF] FAIL
            set /a FAILCOUNT+=1
        ) else (
            echo [%%~nF] PASS
        )
    )
    echo.
)

echo ========================================
if !TESTCOUNT! equ 0 (
    echo No files matched tests\cpp\test_*.cpp
    exit /b 1
)
if !FAILCOUNT! gtr 0 (
    echo Result: !FAILCOUNT! of !TESTCOUNT! failed.
    exit /b 1
)
echo All !TESTCOUNT! tests PASS.
echo ========================================
exit /b 0
