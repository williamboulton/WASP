# Windows System Programming - C++ Development Environment

A C++ development setup for querying Windows 11 system information and processes.

## Prerequisites

### 1. Install Visual Studio 2022 (for MSVC Compiler & Windows SDK)

Download from: https://visualstudio.microsoft.com/downloads/

During installation, select:
- **Desktop development with C++** workload

Ensure these components are installed:
- MSVC v143 C++ build tools (x64/x86)
- Windows 11 SDK (latest)

### 2. Cursor IDE Extensions (Recommended)

Install these extensions for better C++ support:
- **C/C++** by Microsoft (for IntelliSense)
- **C/C++ Extension Pack** (optional, adds more features)

## Project Structure

```
Research/
├── .vscode/
│   ├── c_cpp_properties.json  # IntelliSense configuration
│   └── tasks.json             # Build tasks
├── src/
│   └── main.cpp               # Main source file
├── build/                     # Compiled output (created on build)
├── build.bat                  # Build script (for Dev Command Prompt)
├── build.ps1                  # Build script (PowerShell)
└── README.md
```

## Building the Project

### Option 1: Using Cursor Tasks (Recommended)

1. Press `Ctrl+Shift+B` to run the default build task
2. The PowerShell script will automatically find Visual Studio and compile

### Option 2: Using PowerShell Script

```powershell
.\build.ps1
```

### Option 3: Using Developer Command Prompt

1. Open "x64 Native Tools Command Prompt for VS 2022" from Start Menu
2. Navigate to project directory
3. Run:
```cmd
build.bat
```

### Option 4: Manual Compilation

From Developer Command Prompt:
```cmd
cl /EHsc /W4 /std:c++17 /Fe:build\system_metrics.exe src\system_metrics.cpp /link psapi.lib advapi32.lib ntdll.lib pdh.lib powrprof.lib
```

## Running the Program

After building:
```cmd
.\build\system_metrics.exe install
```

This installs Windows Service SystemMetricsService. Manually start it or stop it via:
```cmd
net start SystemMetricsService
```


**Note:** Some process information requires Administrator privileges for full access.

## Key Windows APIs Used

### Process Enumeration
- `CreateToolhelp32Snapshot()` - Creates a snapshot of processes
- `Process32First()` / `Process32Next()` - Iterates through processes
- `OpenProcess()` - Opens a handle to a process
- `GetProcessMemoryInfo()` - Gets memory usage statistics

### System Information
- `GetSystemInfo()` - CPU architecture, processor count
- `GlobalMemoryStatusEx()` - Memory statistics
- `GetComputerNameA()` - Computer name
- `GetUserNameA()` - Current user name

### Required Headers
```cpp
#include <windows.h>      // Core Windows API
#include <tlhelp32.h>     // Tool Help Library (process enumeration)
#include <psapi.h>        // Process Status API (memory info)
```

### Required Libraries
```cpp
#pragma comment(lib, "psapi.lib")
```

## Troubleshooting

### "cl.exe not found"
The MSVC compiler isn't in your PATH. Solutions:
1. Use "x64 Native Tools Command Prompt for VS 2022"
2. Run `vcvars64.bat` before building
3. Use the PowerShell script which handles this automatically

### IntelliSense errors in Cursor
1. Make sure the C/C++ extension is installed
2. Reload the window after opening the project
3. Check `.vscode/c_cpp_properties.json` paths match your VS installation

### "Access Denied" when querying processes
Some system processes require Administrator privileges. Run the program as Administrator for complete results.

## Useful Resources

- [Windows API Documentation](https://docs.microsoft.com/en-us/windows/win32/api/)
- [Process and Thread Functions](https://docs.microsoft.com/en-us/windows/win32/procthread/process-and-thread-functions)
- [Tool Help Library](https://docs.microsoft.com/en-us/windows/win32/toolhelp/tool-help-library)
- [PSAPI Functions](https://docs.microsoft.com/en-us/windows/win32/psapi/psapi-functions)

