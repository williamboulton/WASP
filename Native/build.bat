@echo off
REM =============================================================================
REM BUILD.BAT - Windows Batch Build Script for C++ System Tools
REM =============================================================================
REM
REM PURPOSE:
REM This batch script compiles the C++ source code using the Microsoft Visual C++
REM (MSVC) compiler. It handles environment setup and provides user feedback.
REM
REM WHAT IS A BATCH FILE?
REM A batch file (.bat or .cmd) is a script containing Windows command-line
REM commands. When you run it, Windows executes each line sequentially.
REM
REM HOW TO RUN THIS SCRIPT:
REM   1. Open "x64 Native Tools Command Prompt for VS 2026" from Start Menu
REM   2. Navigate to the project folder: cd "C:\path\to\project"
REM   3. Run the script: build.bat
REM
REM PREREQUISITES:
REM   - Visual Studio 2026 with "Desktop development with C++" workload
REM   - Windows 11 SDK (included in VS installation)
REM   - Must run from x64 Native Tools Command Prompt (sets up compiler paths)
REM
REM =============================================================================


REM -----------------------------------------------------------------------------
REM LINE: @echo off
REM -----------------------------------------------------------------------------
REM @ symbol: Suppresses displaying this specific command in the console
REM echo off: Prevents all subsequent commands from being displayed
REM 
REM Without this, every command would be printed before execution:
REM   C:\project>where cl >nul 2>nul
REM   C:\project>if errorlevel 1 (...)
REM
REM With "echo off", we only see the output we explicitly print with "echo"
REM -----------------------------------------------------------------------------


REM -----------------------------------------------------------------------------
REM REM - Remark (Comment)
REM -----------------------------------------------------------------------------
REM "REM" stands for "remark" and creates a comment line.
REM Everything after REM is ignored by the batch processor.
REM You can also use :: for comments, but REM is more universally compatible.
REM -----------------------------------------------------------------------------


REM =============================================================================
REM STEP 1: Verify MSVC Compiler is Available
REM =============================================================================
REM Before attempting to compile, we check if the compiler (cl.exe) is in the 
REM system PATH. If not, the user isn't in a Developer Command Prompt.

REM -----------------------------------------------------------------------------
REM COMMAND: where cl >nul 2>nul
REM -----------------------------------------------------------------------------
REM "where" is a Windows command that searches for executable files in PATH
REM Similar to "which" on Linux/Mac
REM
REM "cl" is the MSVC compiler executable name (cl.exe)
REM
REM ">nul" redirects standard output (stdout) to NUL (discards it)
REM   - ">" is the output redirection operator
REM   - "nul" is a special Windows device that discards anything written to it
REM   - Without this, it would print: "C:\Program Files\...\cl.exe"
REM
REM "2>nul" redirects standard error (stderr) to NUL (discards error messages)
REM   - "2>" redirects file descriptor 2 (stderr)
REM   - Without this, if cl.exe isn't found, it would print an error
REM
REM After this command, %ERRORLEVEL% is set to:
REM   - 0 if cl.exe was found
REM   - 1 (or non-zero) if cl.exe was NOT found
REM -----------------------------------------------------------------------------
where cl >nul 2>nul


REM -----------------------------------------------------------------------------
REM COMMAND: if errorlevel 1 ( ... )
REM -----------------------------------------------------------------------------
REM "if errorlevel N" checks if the previous command's exit code is >= N
REM 
REM IMPORTANT: "errorlevel 1" means "errorlevel >= 1", NOT "errorlevel == 1"
REM This is a common source of confusion in batch scripting!
REM
REM The parentheses ( ... ) allow multi-line blocks.
REM Everything between ( and ) is executed if the condition is true.
REM
REM Note: We don't use "if %ERRORLEVEL% NEQ 0" inside parentheses because
REM batch variables expand at parse time, not runtime. Using "errorlevel"
REM keyword avoids this issue.
REM -----------------------------------------------------------------------------
if errorlevel 1 (
    
    REM -------------------------------------------------------------------------
    REM COMMAND: echo ERROR: ...
    REM -------------------------------------------------------------------------
    REM "echo" prints text to the console.
    REM 
    REM Note: We use [cl.exe] instead of (cl.exe) because parentheses have
    REM special meaning in batch files and can cause syntax errors inside
    REM if blocks.
    REM -------------------------------------------------------------------------
    echo ERROR: MSVC compiler [cl.exe] not found in PATH
    
    REM -------------------------------------------------------------------------
    REM COMMAND: echo.
    REM -------------------------------------------------------------------------
    REM "echo." (with a period) prints a blank line.
    REM Plain "echo" without arguments shows "ECHO is on/off".
    REM The period trick produces an empty line.
    REM -------------------------------------------------------------------------
    echo.
    
    echo Please run this from one of these environments:
    echo   1. x64 Native Tools Command Prompt for VS 2026
    echo   2. Developer PowerShell for VS 2026
    echo   3. A terminal where you've run vcvarsall.bat
    echo.
    
    REM -------------------------------------------------------------------------
    REM COMMAND: exit /b 1
    REM -------------------------------------------------------------------------
    REM "exit" terminates the batch script.
    REM 
    REM "/b" flag: Exit only the batch file, not the entire command prompt
    REM   - Without /b, running the script would close your terminal window!
    REM   - With /b, you return to the prompt and can continue working
    REM
    REM "1" is the exit code returned to the caller.
    REM   - 0 = success
    REM   - Non-zero = failure (1 is a generic error)
    REM   - The caller can check this with %ERRORLEVEL%
    REM -------------------------------------------------------------------------
    exit /b 1
)


REM =============================================================================
REM STEP 2: Configure Windows SDK Paths
REM =============================================================================
REM Due to an issue with the VS 2026 environment scripts, the Windows SDK paths
REM are not automatically added to INCLUDE and LIB environment variables.
REM We manually add them here as a workaround.

REM -----------------------------------------------------------------------------
REM COMMAND: set INCLUDE=%INCLUDE%;path1;path2;path3
REM -----------------------------------------------------------------------------
REM "set" assigns a value to an environment variable.
REM 
REM "%INCLUDE%" expands to the current value of the INCLUDE variable.
REM By putting it first, we APPEND our paths instead of REPLACING.
REM
REM ";" is the path separator on Windows (like ":" on Linux).
REM
REM The INCLUDE variable tells the compiler where to find header files (.h).
REM We add three Windows SDK directories:
REM
REM   1. ucrt (Universal C Runtime)
REM      - Contains standard C library headers: stdio.h, stdlib.h, string.h
REM      - "Universal" because it works across all Windows versions
REM
REM   2. um (User Mode)
REM      - Contains Windows API headers: windows.h, tlhelp32.h
REM      - "User mode" = normal application code (vs "kernel mode" for drivers)
REM
REM   3. shared
REM      - Headers shared between user mode and kernel mode
REM      - Common definitions, error codes, etc.
REM
REM "10.0.26100.0" is the Windows SDK version number.
REM Format: 10.0.BUILD.REVISION (matches Windows 10/11 builds)
REM -----------------------------------------------------------------------------
set INCLUDE=%INCLUDE%;C:\Program Files (x86)\Windows Kits\10\Include\10.0.26100.0\ucrt;C:\Program Files (x86)\Windows Kits\10\Include\10.0.26100.0\um;C:\Program Files (x86)\Windows Kits\10\Include\10.0.26100.0\shared


REM -----------------------------------------------------------------------------
REM COMMAND: set LIB=%LIB%;path1;path2
REM -----------------------------------------------------------------------------
REM The LIB variable tells the linker where to find library files (.lib).
REM 
REM We add two Windows SDK library directories (x64 versions):
REM
REM   1. ucrt\x64
REM      - Universal C Runtime libraries
REM      - Basic C functions (printf, malloc, etc.)
REM
REM   2. um\x64
REM      - User mode Windows API libraries
REM      - psapi.lib, advapi32.lib, kernel32.lib, etc.
REM
REM "x64" means 64-bit libraries. For 32-bit, you'd use "x86".
REM The architecture must match your compiler (x64 Native Tools = 64-bit).
REM -----------------------------------------------------------------------------
set LIB=%LIB%;C:\Program Files (x86)\Windows Kits\10\Lib\10.0.26100.0\ucrt\x64;C:\Program Files (x86)\Windows Kits\10\Lib\10.0.26100.0\um\x64


REM =============================================================================
REM STEP 3: Create Output Directory
REM =============================================================================
REM We put compiled files in a "build" folder to keep the source directory clean.

REM -----------------------------------------------------------------------------
REM COMMAND: if not exist "build" mkdir build
REM -----------------------------------------------------------------------------
REM "if not exist" checks if a file/directory does NOT exist.
REM We quote "build" in case there are spaces (good practice).
REM
REM "mkdir" (or "md") creates a directory.
REM Without the "if not exist" check, mkdir would error if the folder exists.
REM
REM Combining them: Only create the folder if it doesn't already exist.
REM -----------------------------------------------------------------------------
if not exist "build" mkdir build


REM =============================================================================
REM STEP 4: Display Compilation Status
REM =============================================================================
REM Let the user know we're about to compile.

echo.
echo Compiling main.cpp...
echo.


REM =============================================================================
REM STEP 5: Compile the C++ Source Code
REM =============================================================================
REM This is the main compilation command using the MSVC compiler.

REM -----------------------------------------------------------------------------
REM COMMAND: cl /EHsc /W4 /std:c++17 /Fe:build\sysinfo.exe src\main.cpp /link psapi.lib advapi32.lib
REM -----------------------------------------------------------------------------
REM "cl" is the Microsoft Visual C++ compiler/linker driver.
REM It handles both compilation (source -> object) and linking (objects -> exe).
REM
REM COMPILER FLAGS (options that start with /):
REM
REM /EHsc - Exception Handling model
REM   - /EH enables C++ exception handling
REM   - 's' = synchronous exception handling (standard C++ exceptions)
REM   - 'c' = assume extern "C" functions never throw exceptions
REM   - Without this, catch blocks might not work correctly
REM
REM /W4 - Warning Level 4
REM   - Warning levels range from /W0 (none) to /W4 (most warnings)
REM   - /W4 catches many potential bugs: unused variables, sign mismatches, etc.
REM   - There's also /Wall (ALL warnings), but it's often too noisy
REM
REM /std:c++17 - C++ Language Standard
REM   - Use C++17 features (auto, structured bindings, if constexpr, etc.)
REM   - Options: /std:c++14, /std:c++17, /std:c++20, /std:c++latest
REM   - Default varies by VS version
REM
REM /Fe:build\sysinfo.exe - Output File name
REM   - /Fe specifies the executable name (Fe = "File executable")
REM   - Colon after /Fe is optional: /Fe:name and /Fename both work
REM   - Without this, output would be "main.exe" (based on first source file)
REM
REM src\main.cpp - Input Source File
REM   - The C++ file to compile
REM   - Can specify multiple files: file1.cpp file2.cpp file3.cpp
REM
REM /link - Pass following arguments to the linker
REM   - Everything after /link goes to link.exe, not cl.exe
REM   - Used to specify libraries, linker options, etc.
REM
REM psapi.lib - Process Status API Library
REM   - Required for GetProcessMemoryInfo() function
REM   - This is an "import library" that tells the linker about psapi.dll
REM
REM advapi32.lib - Advanced Windows 32 API Library  
REM   - Required for GetUserNameA() function
REM   - Contains security, registry, and user account functions
REM
REM WHAT HAPPENS DURING COMPILATION:
REM   1. Preprocessing: #include files are inserted, #define macros expanded
REM   2. Compilation: C++ code -> assembly -> object file (main.obj)
REM   3. Linking: Object files + libraries -> executable (sysinfo.exe)
REM
REM OUTPUT FILES CREATED:
REM   - build\sysinfo.exe - The final executable
REM   - main.obj - Intermediate object file (in current directory)
REM -----------------------------------------------------------------------------
cl /EHsc /W4 /std:c++17 /Fe:build\sysinfo.exe src\main.cpp /link psapi.lib advapi32.lib


REM =============================================================================
REM STEP 6: Check Compilation Result
REM =============================================================================
REM The compiler sets ERRORLEVEL to 0 on success, non-zero on failure.

REM -----------------------------------------------------------------------------
REM COMMAND: if errorlevel 1 ( ... )
REM -----------------------------------------------------------------------------
REM Check if compilation failed (exit code >= 1).
REM If it failed, print error message and exit with error code.
REM -----------------------------------------------------------------------------
if errorlevel 1 (
    echo.
    echo Build failed!
    exit /b 1
)


REM =============================================================================
REM STEP 7: Display Success Message
REM =============================================================================
REM If we reach here, compilation succeeded!

echo.
echo ========================================
echo Build successful!
echo Output: build\sysinfo.exe
echo ========================================
echo.


REM =============================================================================
REM END OF SCRIPT
REM =============================================================================
REM When the script ends without an explicit "exit", it returns errorlevel 0
REM (success) to the caller.
REM
REM TO RUN THE COMPILED PROGRAM:
REM   build\sysinfo.exe
REM
REM TROUBLESHOOTING:
REM   - "cl is not recognized": Not in Developer Command Prompt
REM   - "Cannot open include file": Windows SDK paths not set correctly
REM   - "unresolved external symbol": Missing library in /link section
REM =============================================================================
