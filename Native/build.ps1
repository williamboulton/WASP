# =============================================================================
# BUILD.PS1 - PowerShell Build Script for C++ System Tools
# =============================================================================
#
# PURPOSE:
# This PowerShell script compiles C++ source code using the Microsoft Visual C++
# (MSVC) compiler. It automatically finds Visual Studio and sets up the build
# environment, making it more user-friendly than the batch file version.
#
# WHAT IS POWERSHELL?
# PowerShell is a modern command-line shell and scripting language from Microsoft.
# It's more powerful than batch files because:
#   - It's object-oriented (commands return objects, not just text)
#   - It has a consistent verb-noun command naming (Get-Item, Set-Content, etc.)
#   - It has better error handling with try/catch blocks
#   - It can use .NET Framework classes directly
#
# HOW TO RUN THIS SCRIPT:
#   1. Open any PowerShell terminal (doesn't need to be Developer PowerShell)
#   2. Navigate to the project folder: cd "C:\path\to\project"
#   3. Run the script: .\build.ps1
#
# IF YOU GET "SCRIPT EXECUTION DISABLED" ERROR:
#   Run: Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
#   This allows running local scripts while blocking downloaded unsigned scripts.
#
# PREREQUISITES:
#   - Visual Studio 2022/2026 with "Desktop development with C++" workload
#   - Windows 11 SDK (included in VS installation)
#
# =============================================================================


# =============================================================================
# SECTION 1: Find Visual Studio Installation
# =============================================================================
# Visual Studio can be installed in different locations and editions.
# We use "vswhere.exe" (Visual Studio Where) to find it automatically.


# -----------------------------------------------------------------------------
# Variable: $vsWhere
# -----------------------------------------------------------------------------
# This stores the path to the vswhere.exe utility.
#
# WHAT IS VSWHERE?
# vswhere.exe is a tool installed with Visual Studio that helps locate VS installations.
# It's the official Microsoft-recommended way to find Visual Studio programmatically.
#
# ${env:ProgramFiles(x86)} - This is how PowerShell accesses environment variables
#   - "env:" is a PowerShell drive that contains environment variables
#   - "ProgramFiles(x86)" is the variable name (usually "C:\Program Files (x86)")
#   - The ${} syntax is needed because the variable name contains parentheses
#
# TYPICAL PATH: C:\Program Files (x86)\Microsoft Visual Studio\Installer\vswhere.exe
# -----------------------------------------------------------------------------
$vsWhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"


# -----------------------------------------------------------------------------
# Conditional: Check if vswhere.exe exists
# -----------------------------------------------------------------------------
# Test-Path returns $true if the file/folder exists, $false otherwise.
# This is equivalent to "if exist" in batch files.
# -----------------------------------------------------------------------------
if (Test-Path $vsWhere) {
    
    # -------------------------------------------------------------------------
    # Find Visual Studio installation path using vswhere
    # -------------------------------------------------------------------------
    # The & operator in PowerShell is the "call operator" - it executes a command.
    # We need it here because $vsWhere is a string containing a path, not a cmdlet.
    #
    # vswhere arguments:
    #   -latest               : Get the most recently installed version
    #   -property installationPath : Return only the installation path
    #
    # Example output: "C:\Program Files\Microsoft Visual Studio\2022\Community"
    #
    # Without -property, vswhere returns JSON/text with all VS information.
    # Other useful -property values: productPath, catalog_productDisplayVersion
    # -------------------------------------------------------------------------
    $vsPath = & $vsWhere -latest -property installationPath
    
    # -------------------------------------------------------------------------
    # Display found path using Write-Host
    # -------------------------------------------------------------------------
    # Write-Host outputs text to the console (similar to echo/print).
    #
    # -ForegroundColor parameter sets the text color.
    # Available colors: Black, DarkBlue, DarkGreen, DarkCyan, DarkRed, 
    #                   DarkMagenta, DarkYellow, Gray, DarkGray, Blue,
    #                   Green, Cyan, Red, Magenta, Yellow, White
    # -------------------------------------------------------------------------
    Write-Host "Found Visual Studio at: $vsPath" -ForegroundColor Cyan
    
} else {
    
    # -------------------------------------------------------------------------
    # Fallback: Use a default path if vswhere is not found
    # -------------------------------------------------------------------------
    # This might happen if:
    #   - Visual Studio was installed without the installer component
    #   - VS was installed to a non-standard location
    #   - Only Build Tools were installed (not full VS)
    #
    # We use a common default path. The user will get an error later if it's wrong.
    # -------------------------------------------------------------------------
    $vsPath = "C:\Program Files\Microsoft Visual Studio\2022\Community"
}


# =============================================================================
# SECTION 2: Locate vcvars64.bat
# =============================================================================
# vcvars64.bat is a batch file that sets up environment variables for 64-bit
# compilation. It sets PATH, INCLUDE, LIB, and other variables.


# -----------------------------------------------------------------------------
# Variable: $vcvarsall - Path to vcvars64.bat
# -----------------------------------------------------------------------------
# Join-Path combines path components correctly (handles slashes, etc.)
# It's safer than string concatenation: Join-Path "C:\foo" "bar" = "C:\foo\bar"
#
# vcvars64.bat location: <VS Install>\VC\Auxiliary\Build\vcvars64.bat
#
# Other vcvars files available:
#   - vcvars32.bat    : 32-bit x86 compiler
#   - vcvars64.bat    : 64-bit x64 compiler (what we use)
#   - vcvarsall.bat   : Universal script that takes architecture as argument
#   - vcvarsamd64_x86.bat : Cross-compile: 64-bit host, 32-bit target
#   - vcvarsx86_amd64.bat : Cross-compile: 32-bit host, 64-bit target
# -----------------------------------------------------------------------------
$vcvarsall = Join-Path $vsPath "VC\Auxiliary\Build\vcvars64.bat"


# -----------------------------------------------------------------------------
# Conditional: Verify vcvars64.bat exists
# -----------------------------------------------------------------------------
# -not inverts the boolean result of Test-Path
# If the file doesn't exist, we can't compile, so we exit with an error.
# -----------------------------------------------------------------------------
if (-not (Test-Path $vcvarsall)) {
    
    # -------------------------------------------------------------------------
    # Display error message and exit
    # -------------------------------------------------------------------------
    # Write-Host with -ForegroundColor Red makes it stand out as an error.
    # Yellow is used for additional information/hints.
    # -------------------------------------------------------------------------
    Write-Host "ERROR: Could not find vcvars64.bat at $vcvarsall" -ForegroundColor Red
    Write-Host "Please ensure Visual Studio with C++ tools is installed." -ForegroundColor Yellow
    
    # -------------------------------------------------------------------------
    # Exit with error code
    # -------------------------------------------------------------------------
    # "exit 1" terminates the script with exit code 1 (error).
    # The caller can check $LASTEXITCODE to see if the script succeeded.
    # -------------------------------------------------------------------------
    exit 1
}


# =============================================================================
# SECTION 3: Create Build Directory
# =============================================================================
# We store compiled output in a "build" folder to keep things organized.


# -----------------------------------------------------------------------------
# Conditional: Create build directory if it doesn't exist
# -----------------------------------------------------------------------------
if (-not (Test-Path "build")) {
    
    # -------------------------------------------------------------------------
    # Create the directory using New-Item
    # -------------------------------------------------------------------------
    # New-Item creates files, directories, or other items.
    #
    # -ItemType Directory : Create a folder (not a file)
    # -Path "build"       : Name/path of the folder to create
    #
    # | Out-Null : Pipe output to Out-Null to suppress the output
    #   - New-Item normally returns information about what it created
    #   - We don't need to see that, so we discard it
    #   - This is cleaner than letting it print to the console
    # -------------------------------------------------------------------------
    New-Item -ItemType Directory -Path "build" | Out-Null
}


# =============================================================================
# SECTION 4: Display Build Header
# =============================================================================
# Show a nice header so the user knows compilation is starting.


# -----------------------------------------------------------------------------
# Write-Host with formatting
# -----------------------------------------------------------------------------
# `n is PowerShell's escape sequence for a newline character.
# (In batch files and C, this would be \n)
#
# PowerShell escape sequences:
#   `n = newline
#   `t = tab
#   `r = carriage return
#   `" = literal quote inside double-quoted string
#   `` = literal backtick
# -----------------------------------------------------------------------------
Write-Host "`n========================================" -ForegroundColor Green
Write-Host "Building Windows System Info Tool" -ForegroundColor Green
Write-Host "========================================`n" -ForegroundColor Green


# =============================================================================
# SECTION 5: Build the Compilation Command
# =============================================================================
# We need to run cl.exe (the compiler), but it requires the environment set up
# by vcvars64.bat. Since vcvars64.bat is a batch file that modifies environment
# variables, we need to run it in the same cmd.exe session as the compiler.


# -----------------------------------------------------------------------------
# Variable: $compileCmd - The command string to execute
# -----------------------------------------------------------------------------
# @" ... "@ is a PowerShell "here-string" (multi-line string literal).
# It preserves whitespace and allows quotes inside without escaping.
#
# THE COMMAND BREAKDOWN:
#
# call "$vcvarsall" >nul 2>&1
#   - "call" runs a batch file and returns to continue
#   - "$vcvarsall" expands to the full path of vcvars64.bat
#   - ">nul 2>&1" suppresses all output (vcvars64.bat is chatty)
#     - >nul redirects stdout to NUL (discards it)
#     - 2>&1 redirects stderr to wherever stdout goes (also NUL)
#
# && 
#   - Conditional execution: only run the next command if the previous succeeded
#   - If vcvars64.bat fails, cl.exe won't run
#
# cl /EHsc /W4 /std:c++17 /Fe:build\sysinfo.exe src\main.cpp /link psapi.lib
#   - See build.bat comments for detailed explanation of each flag
#   - Note: We're not including advapi32.lib here because the PowerShell
#     script was written before we discovered we needed it.
#     (We should add it for consistency!)
#
# WHY USE cmd /c?
# PowerShell can't directly source batch files into its environment.
# By running everything through "cmd /c", we let cmd.exe handle the batch file
# and run the compiler in that environment.
# -----------------------------------------------------------------------------
$compileCmd = @"
call "$vcvarsall" >nul 2>&1 && cl /EHsc /W4 /std:c++17 /Fe:build\sysinfo.exe src\main.cpp /link psapi.lib advapi32.lib
"@


# =============================================================================
# SECTION 6: Execute the Compilation
# =============================================================================


# -----------------------------------------------------------------------------
# Command: cmd /c $compileCmd
# -----------------------------------------------------------------------------
# "cmd" is the old Windows command interpreter (cmd.exe).
# 
# /c flag means:
#   - Execute the following command string
#   - Then terminate cmd.exe
#   - (Contrast with /k which keeps the cmd window open)
#
# $compileCmd is our here-string containing the full command.
#
# After this runs, $LASTEXITCODE will contain the exit code:
#   - 0 = success
#   - Non-zero = failure (typically 1 for compiler errors)
#
# $LASTEXITCODE is an automatic PowerShell variable that stores the exit code
# of the last native command (non-PowerShell executable).
# -----------------------------------------------------------------------------
cmd /c $compileCmd


# =============================================================================
# SECTION 7: Check Result and Provide Feedback
# =============================================================================


# -----------------------------------------------------------------------------
# Conditional: Check if compilation succeeded
# -----------------------------------------------------------------------------
# -eq is PowerShell's equality operator (equals).
# Other comparison operators:
#   -ne : not equal
#   -lt : less than
#   -gt : greater than
#   -le : less than or equal
#   -ge : greater than or equal
#
# Note: PowerShell uses -eq instead of == because < and > are reserved for
# redirection operators.
# -----------------------------------------------------------------------------
if ($LASTEXITCODE -eq 0) {
    
    # -------------------------------------------------------------------------
    # Success: Display success message
    # -------------------------------------------------------------------------
    Write-Host "`n========================================" -ForegroundColor Green
    Write-Host "Build successful!" -ForegroundColor Green
    Write-Host "Output: build\sysinfo.exe" -ForegroundColor Green
    Write-Host "========================================`n" -ForegroundColor Green
    
    # -------------------------------------------------------------------------
    # Prompt user to run the program
    # -------------------------------------------------------------------------
    # Read-Host displays a prompt and waits for user input.
    # It returns the string the user typed (before pressing Enter).
    #
    # This is a convenience feature - the user can immediately test the build.
    # -------------------------------------------------------------------------
    $run = Read-Host "Run the program now? (y/n)"
    
    # -------------------------------------------------------------------------
    # Conditional: Check if user wants to run
    # -------------------------------------------------------------------------
    # -or is the logical OR operator.
    # We check for both lowercase 'y' and uppercase 'Y' for user convenience.
    # -------------------------------------------------------------------------
    if ($run -eq 'y' -or $run -eq 'Y') {
        
        # ---------------------------------------------------------------------
        # Run the compiled executable
        # ---------------------------------------------------------------------
        # & is the call operator - it executes the command.
        # .\ means "current directory" (similar to ./ on Linux)
        #
        # We need & because the path is a string. Without it, PowerShell
        # would try to interpret .\build\sysinfo.exe as a string to output.
        # ---------------------------------------------------------------------
        Write-Host "`nRunning sysinfo.exe...`n" -ForegroundColor Cyan
        & .\build\sysinfo.exe
    }
    
} else {
    
    # -------------------------------------------------------------------------
    # Failure: Display error and exit
    # -------------------------------------------------------------------------
    Write-Host "`nBuild failed!" -ForegroundColor Red
    exit 1
}


# =============================================================================
# END OF SCRIPT
# =============================================================================
#
# POWERSHELL VS BATCH FILE COMPARISON:
#
# | Feature              | Batch (.bat)      | PowerShell (.ps1)          |
# |----------------------|-------------------|----------------------------|
# | Comments             | REM or ::         | # (hash)                   |
# | Variables            | %varname%         | $varname                   |
# | Environment vars     | %PATH%            | $env:PATH                  |
# | If statement         | if ... (...)      | if (...) { ... }           |
# | Equality check       | EQU or ==         | -eq                        |
# | String concatenation | %a%%b%            | "$a$b" or $a + $b          |
# | Command output       | for /f            | $result = command          |
# | Suppress output      | >nul              | | Out-Null                  |
# | Exit script          | exit /b N         | exit N                     |
# | Check exit code      | %ERRORLEVEL%      | $LASTEXITCODE              |
#
# ADVANTAGES OF THIS SCRIPT OVER build.bat:
#   1. Automatically finds Visual Studio (no need for Developer Command Prompt)
#   2. More readable syntax
#   3. Color-coded output
#   4. Prompts to run the program after building
#
# DISADVANTAGES:
#   1. Requires PowerShell execution policy to be set
#   2. Slightly slower to start (PowerShell loads .NET runtime)
#   3. Users may be less familiar with PowerShell syntax
#
# =============================================================================
