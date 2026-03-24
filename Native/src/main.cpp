/**
 * =============================================================================
 * Windows System Information & Process Query Tool
 * =============================================================================
 * 
 * PURPOSE:
 * This program demonstrates how to use the Windows API (Win32 API) to:
 *   1. Query hardware information (CPU architecture, processor count, memory)
 *   2. Enumerate all currently running processes on the system
 *   3. Retrieve memory usage statistics for each process
 * 
 * BACKGROUND - What is the Windows API?
 * The Windows API (formerly called Win32 API) is a set of C functions provided
 * by Microsoft that allow programs to interact with the Windows operating system.
 * These functions are declared in header files (like windows.h) and implemented
 * in DLL files (like kernel32.dll, user32.dll, advapi32.dll).
 * 
 * KEY CONCEPTS:
 * - HANDLE: A pointer-like value that represents a system resource (file, process, etc.)
 * - DWORD: A 32-bit unsigned integer (Double WORD, where WORD = 16 bits)
 * - BOOL: Windows boolean type (actually an int: 0 = FALSE, non-zero = TRUE)
 * - LP prefix: "Long Pointer" - a pointer type (e.g., LPSTR = pointer to string)
 * - Structures: Windows uses many structs to pass data to/from API functions
 * 
 * COMPILATION:
 * This file is compiled using the Microsoft Visual C++ (MSVC) compiler.
 * Command: cl /EHsc /W4 /std:c++17 main.cpp /link psapi.lib advapi32.lib
 * 
 * Compiler flags explained:
 *   /EHsc    - Enable C++ exception handling (s=synchronous, c=extern "C" functions don't throw)
 *   /W4      - Warning level 4 (high verbosity - catches many potential issues)
 *   /std:c++17 - Use C++17 standard (enables modern C++ features)
 *   /Fe:     - Specify output executable name
 *   /link    - Pass following arguments to the linker
 * 
 * Required libraries:
 *   psapi.lib    - Process Status API (for GetProcessMemoryInfo)
 *   advapi32.lib - Advanced Windows 32 API (for GetUserNameA)
 * 
 * =============================================================================
 */

/* =============================================================================
 * SECTION 1: HEADER FILES (Include Directives)
 * =============================================================================
 * Header files contain declarations (function prototypes, type definitions,
 * constants) that tell the compiler what functions and types exist.
 * The actual code (implementation) is in DLL files linked at runtime.
 */

/**
 * windows.h - The master Windows header file
 * 
 * This single header includes most of the commonly used Windows API declarations:
 * - Basic Windows types (HANDLE, DWORD, BOOL, etc.)
 * - Kernel functions (memory management, process/thread functions)
 * - System information functions (GetSystemInfo, GlobalMemoryStatusEx)
 * - Many other subsystems
 * 
 * Note: windows.h can significantly increase compile time because it's huge.
 * In production, you might use WIN32_LEAN_AND_MEAN to exclude rarely-used parts.
 */
#include <windows.h>

/**
 * tlhelp32.h - Tool Help Library (32-bit)
 * 
 * Despite the "32" in the name, this works on 64-bit Windows too.
 * Provides functions for taking "snapshots" of the system state:
 * - CreateToolhelp32Snapshot() - Creates a snapshot of processes, threads, modules, or heaps
 * - Process32First() / Process32Next() - Iterate through processes in a snapshot
 * - Thread32First() / Thread32Next() - Iterate through threads
 * - Module32First() / Module32Next() - Iterate through modules (DLLs) loaded by a process
 * 
 * The "snapshot" approach is important: it captures the state at a moment in time,
 * so you're iterating through a consistent view even if processes start/stop.
 */
#include <tlhelp32.h>

/**
 * psapi.h - Process Status API
 * 
 * Provides functions for getting detailed information about processes:
 * - GetProcessMemoryInfo() - Memory usage (working set, page file usage, etc.)
 * - EnumProcesses() - Alternative way to enumerate process IDs
 * - EnumProcessModules() - List DLLs loaded by a process
 * - GetModuleFileNameEx() - Get full path of a process's executable
 * 
 * Requires linking with psapi.lib (on older Windows) or is part of kernel32.lib (Windows 7+)
 */
#include <psapi.h>

/**
 * iostream - C++ Standard Input/Output Streams
 * 
 * Provides std::cout (console output), std::cin (console input), std::cerr (error output)
 * These are C++ stream objects that make formatted I/O easier than C's printf/scanf.
 */
#include <iostream>

/**
 * iomanip - I/O Manipulators
 * 
 * Provides formatting manipulators for streams:
 * - std::setw(n) - Set field width to n characters
 * - std::left / std::right - Alignment within field
 * - std::setprecision(n) - Decimal places for floating point
 * - std::fixed - Use fixed-point notation (not scientific)
 */
#include <iomanip>

/**
 * string - C++ String Class
 * 
 * Provides std::string, a dynamic string class that manages its own memory.
 * Much safer and easier to use than C-style char arrays.
 */
#include <string>

/**
 * vector - C++ Dynamic Array
 * 
 * Provides std::vector<T>, a resizable array that manages its own memory.
 * Automatically grows as you add elements with push_back().
 */
#include <vector>


/* =============================================================================
 * SECTION 2: LIBRARY LINKING (Pragma Directives)
 * =============================================================================
 * These #pragma directives tell the linker which .lib files to include.
 * This is an alternative to specifying them on the command line.
 * The advantage is that the dependencies are documented in the source code.
 */

/**
 * psapi.lib - Process Status API library
 * 
 * Contains the implementation of functions declared in psapi.h.
 * Specifically, we need this for GetProcessMemoryInfo().
 * 
 * Technical note: .lib files can be either:
 * 1. Static libraries (code is copied into your .exe)
 * 2. Import libraries (contains stubs that load a DLL at runtime)
 * psapi.lib is an import library that loads psapi.dll at runtime.
 */
#pragma comment(lib, "psapi.lib")

/**
 * advapi32.lib - Advanced Windows 32 Base API library
 * 
 * Contains "advanced" Windows functions including:
 * - Security functions (access tokens, privileges)
 * - Registry functions (RegOpenKey, RegQueryValue, etc.)
 * - User account functions (GetUserNameA - which we use)
 * - Service Control Manager functions
 * - Event logging functions
 * 
 * The 'A' suffix in GetUserNameA means "ANSI" (8-bit characters).
 * There's also GetUserNameW for "Wide" (16-bit Unicode) characters.
 */
#pragma comment(lib, "advapi32.lib")


/* =============================================================================
 * SECTION 3: FUNCTION - DisplaySystemInfo()
 * =============================================================================
 * This function queries and displays information about the computer's hardware
 * and current user. It demonstrates several Windows API functions.
 */

/**
 * DisplaySystemInfo - Queries and prints system hardware and user information
 * 
 * This function demonstrates the following Windows API calls:
 * 1. GetSystemInfo() - CPU architecture and processor count
 * 2. GlobalMemoryStatusEx() - RAM usage statistics
 * 3. GetComputerNameA() - Machine's network name
 * 4. GetUserNameA() - Currently logged-in user
 * 
 * Parameters: None
 * Returns: void (outputs directly to console)
 */
void DisplaySystemInfo() {
    
    // Print a header to visually separate this section in the output
    std::cout << "\n========== MY SYSTEM INFORMATION ==========\n\n";
    
    /* -------------------------------------------------------------------------
     * PART A: Get CPU/Processor Information using GetSystemInfo()
     * -------------------------------------------------------------------------
     * GetSystemInfo() fills a SYSTEM_INFO structure with hardware details.
     * 
     * The SYSTEM_INFO structure contains:
     * - wProcessorArchitecture: CPU type (x86, x64, ARM, etc.)
     * - dwNumberOfProcessors: How many logical processors (cores/threads)
     * - dwPageSize: Memory page size (usually 4096 bytes = 4KB)
     * - lpMinimumApplicationAddress: Lowest usable memory address
     * - lpMaximumApplicationAddress: Highest usable memory address
     * - dwActiveProcessorMask: Bitmask of which processors are active
     * - And more...
     */
    
    // Declare a SYSTEM_INFO structure to receive the data
    // SYSTEM_INFO is a struct defined in windows.h
    SYSTEM_INFO sysInfo;
    
    // Call GetSystemInfo() - it takes a pointer to our structure and fills it
    // This function always succeeds (no return value to check)
    GetSystemInfo(&sysInfo);
    
    // Display the processor architecture
    // wProcessorArchitecture is a WORD (16-bit unsigned int) containing a constant
    std::cout << "Processor Architecture: ";
    
    // Use a switch statement to convert the numeric constant to a readable string
    // These constants (PROCESSOR_ARCHITECTURE_*) are defined in windows.h
    switch (sysInfo.wProcessorArchitecture) {
        case PROCESSOR_ARCHITECTURE_AMD64:  // Value: 9
            // x64 = 64-bit Intel/AMD processors (most desktop/laptop PCs)
            std::cout << "x64 (AMD64)"; 
            break;
        case PROCESSOR_ARCHITECTURE_ARM64:  // Value: 12
            // ARM64 = 64-bit ARM processors (Surface Pro X, some laptops)
            std::cout << "ARM64"; 
            break;
        case PROCESSOR_ARCHITECTURE_INTEL:  // Value: 0
            // x86 = 32-bit Intel processors (older systems)
            std::cout << "x86"; 
            break;
        default: 
            // Unknown architecture (shouldn't happen on normal Windows PCs)
            std::cout << "Unknown"; 
            break;
    }
    std::cout << "\n";  // End the line
    
    // Display the number of logical processors
    // dwNumberOfProcessors counts logical processors, not physical cores
    // Example: A 4-core CPU with hyperthreading shows 8 processors
    std::cout << "Number of Processors: " << sysInfo.dwNumberOfProcessors << "\n";
    
    // Display the memory page size
    // A "page" is the smallest unit of memory the OS manages
    // dwPageSize is typically 4096 bytes (4 KB) on most systems
    // This is important for understanding memory allocation behavior
    std::cout << "Page Size: " << sysInfo.dwPageSize << " bytes\n";
    
    /* -------------------------------------------------------------------------
     * PART B: Get Memory Information using GlobalMemoryStatusEx()
     * -------------------------------------------------------------------------
     * GlobalMemoryStatusEx() provides detailed RAM and virtual memory statistics.
     * The "Ex" suffix indicates this is the extended version that supports >4GB RAM.
     * (The older GlobalMemoryStatus() function couldn't handle large memory sizes)
     * 
     * MEMORYSTATUSEX structure contains:
     * - dwLength: Size of this structure (MUST be set before calling)
     * - dwMemoryLoad: Percentage of physical memory in use (0-100)
     * - ullTotalPhys: Total physical RAM in bytes
     * - ullAvailPhys: Available physical RAM in bytes
     * - ullTotalPageFile: Total page file (swap) size in bytes
     * - ullAvailPageFile: Available page file space in bytes
     * - ullTotalVirtual: Total virtual address space for this process
     * - ullAvailVirtual: Available virtual address space for this process
     * 
     * Note: "ull" prefix means "unsigned long long" (64-bit unsigned integer)
     */
    
    // Declare a MEMORYSTATUSEX structure to receive the data
    MEMORYSTATUSEX memInfo;
    
    // CRITICAL: You MUST set dwLength before calling GlobalMemoryStatusEx()
    // This is a common pattern in Windows API - it allows for structure versioning
    // The function checks this value to know which version of the structure you're using
    memInfo.dwLength = sizeof(MEMORYSTATUSEX);
    
    // Call GlobalMemoryStatusEx() - returns TRUE on success, FALSE on failure
    // We pass a pointer to our structure
    if (GlobalMemoryStatusEx(&memInfo)) {
        // Success - display the memory information
        
        std::cout << "\n--- Memory Status ---\n";
        
        // dwMemoryLoad: What percentage of RAM is currently in use
        // This is a quick way to see if the system is under memory pressure
        std::cout << "Memory Load: " << memInfo.dwMemoryLoad << "%\n";
        
        // ullTotalPhys: Total installed RAM
        // Divide by (1024 * 1024) to convert bytes to megabytes
        // We could also divide by (1024 * 1024 * 1024) for gigabytes
        std::cout << "Total Physical Memory: " 
                  << (memInfo.ullTotalPhys / (1024 * 1024)) << " MB\n";
        
        // ullAvailPhys: RAM that's currently free and available
        // Note: "Available" memory includes standby cache that can be reclaimed
        std::cout << "Available Physical Memory: " 
                  << (memInfo.ullAvailPhys / (1024 * 1024)) << " MB\n";
        
        // ullTotalVirtual: Virtual address space available to this process
        // On 64-bit Windows, this is typically 128 TB (terabytes)!
        // On 32-bit Windows, this is typically 2-4 GB
        std::cout << "Total Virtual Memory: " 
                  << (memInfo.ullTotalVirtual / (1024 * 1024)) << " MB\n";
    }
    // If GlobalMemoryStatusEx() fails, we silently skip this section
    // In production code, you might want to log an error
    
    /* -------------------------------------------------------------------------
     * PART C: Get Computer Name using GetComputerNameA()
     * -------------------------------------------------------------------------
     * GetComputerNameA() retrieves the NetBIOS name of the local computer.
     * This is the name you see in network settings and when sharing files.
     * 
     * The 'A' suffix means ANSI (8-bit characters).
     * GetComputerNameW() would return a Unicode (16-bit) string.
     * 
     * Parameters:
     * - lpBuffer: Pointer to a buffer that receives the computer name
     * - nSize: Pointer to a DWORD containing buffer size; receives actual length
     * 
     * Returns: TRUE on success, FALSE on failure
     */
    
    // Declare a buffer to hold the computer name
    // MAX_COMPUTERNAME_LENGTH is defined in windows.h (typically 15 characters)
    // We add 1 for the null terminator character
    char computerName[MAX_COMPUTERNAME_LENGTH + 1];
    
    // Declare a variable to hold the buffer size
    // This is both input (buffer capacity) and output (actual string length)
    DWORD size = sizeof(computerName);
    
    // Call GetComputerNameA() - returns TRUE on success
    if (GetComputerNameA(computerName, &size)) {
        // Success - computerName now contains the name, 'size' contains the length
        std::cout << "\nComputer Name: " << computerName << "\n";
    }
    // If it fails, we silently skip (could happen if buffer too small, but unlikely)
    
    /* -------------------------------------------------------------------------
     * PART D: Get Current User Name using GetUserNameA()
     * -------------------------------------------------------------------------
     * GetUserNameA() retrieves the name of the user currently logged in.
     * This is the account name, not the display name.
     * 
     * This function requires advapi32.lib (Advanced API library).
     * 
     * Parameters:
     * - lpBuffer: Pointer to a buffer that receives the user name
     * - pcbBuffer: Pointer to a DWORD containing buffer size; receives actual length
     * 
     * Returns: TRUE on success, FALSE on failure (e.g., buffer too small)
     */
    
    // Declare a buffer to hold the user name
    // There's no MAX_USERNAME_LENGTH constant, so we use a reasonable size
    // Windows user names can be up to 256 characters in some cases
    char userName[256];
    
    // Buffer size variable - both input and output
    DWORD userSize = sizeof(userName);
    
    // Call GetUserNameA() - returns TRUE on success
    if (GetUserNameA(userName, &userSize)) {
        // Success - userName now contains the logged-in user's account name
        std::cout << "Current User: " << userName << "\n";
    }
}


/* =============================================================================
 * SECTION 4: DATA STRUCTURE - ProcessInfo
 * =============================================================================
 * We define our own structure to hold information about a single process.
 * This makes it easier to collect all processes into a vector and work with them.
 */

/**
 * ProcessInfo - Custom structure to hold process data
 * 
 * This is a simple C++ struct (all members public by default) that stores:
 * - pid: Process ID (unique identifier assigned by Windows)
 * - name: Executable name (e.g., "notepad.exe")
 * - memoryUsage: Working set size in bytes (RAM currently being used)
 * 
 * We use std::string for the name because it's safer and easier than char arrays.
 * SIZE_T is used for memory because it matches the type returned by Windows APIs.
 */
struct ProcessInfo {
    DWORD pid;           // Process ID - Windows uses DWORD (32-bit unsigned) for PIDs
    std::string name;    // Process executable name (e.g., "chrome.exe")
    SIZE_T memoryUsage;  // Working set size in bytes (SIZE_T = size_t on Windows)
};


/* =============================================================================
 * SECTION 5: FUNCTION - GetRunningProcesses()
 * =============================================================================
 * This function enumerates all running processes and returns them as a vector.
 * It demonstrates the Tool Help Library (tlhelp32.h) for process enumeration.
 */

/**
 * GetRunningProcesses - Enumerates all processes currently running on the system
 * 
 * This function uses the Tool Help Library to take a "snapshot" of all processes
 * and iterate through them. For each process, it also tries to query memory usage.
 * 
 * Algorithm:
 * 1. Create a snapshot of all processes using CreateToolhelp32Snapshot()
 * 2. Initialize a PROCESSENTRY32 structure
 * 3. Get the first process with Process32First()
 * 4. Loop through remaining processes with Process32Next()
 * 5. For each process, try to open it and get memory info
 * 6. Close the snapshot handle when done
 * 
 * Returns: std::vector<ProcessInfo> containing all found processes
 *          (may be empty if snapshot creation fails)
 */
std::vector<ProcessInfo> GetRunningProcesses() {
    
    // Create an empty vector to store our process information
    // We'll add processes to this as we find them
    std::vector<ProcessInfo> processes;
    
    /* -------------------------------------------------------------------------
     * STEP 1: Create a process snapshot using CreateToolhelp32Snapshot()
     * -------------------------------------------------------------------------
     * A "snapshot" is a frozen-in-time view of system state.
     * While we iterate, new processes might start or stop, but our snapshot
     * remains consistent.
     * 
     * Parameters:
     * - dwFlags: What to include in snapshot. Options include:
     *   - TH32CS_SNAPPROCESS: Include all processes
     *   - TH32CS_SNAPTHREAD: Include all threads
     *   - TH32CS_SNAPMODULE: Include modules (DLLs) of specified process
     *   - TH32CS_SNAPHEAPLIST: Include heap list of specified process
     * - th32ProcessID: Process ID to snapshot (0 = all processes for TH32CS_SNAPPROCESS)
     * 
     * Returns: HANDLE to the snapshot, or INVALID_HANDLE_VALUE on failure
     * 
     * IMPORTANT: You MUST call CloseHandle() on this when done!
     */
    HANDLE hSnapshot = CreateToolhelp32Snapshot(
        TH32CS_SNAPPROCESS,  // We want to snapshot all processes
        0                     // 0 means "all processes" (would be a PID for module/heap snapshots)
    );
    
    // Check if snapshot creation succeeded
    // INVALID_HANDLE_VALUE is a special constant meaning "no valid handle"
    if (hSnapshot == INVALID_HANDLE_VALUE) {
        // Failed to create snapshot - print error and return empty vector
        // GetLastError() returns a numeric error code explaining what went wrong
        std::cerr << "Failed to create process snapshot. Error: " 
                  << GetLastError() << "\n";
        return processes;  // Return empty vector
    }
    
    /* -------------------------------------------------------------------------
     * STEP 2: Initialize the PROCESSENTRY32 structure
     * -------------------------------------------------------------------------
     * PROCESSENTRY32 is the structure that receives process information.
     * 
     * Key fields:
     * - dwSize: MUST be set to sizeof(PROCESSENTRY32) before calling Process32First
     * - th32ProcessID: Process ID (PID)
     * - th32ParentProcessID: PID of parent process
     * - cntThreads: Number of threads in this process
     * - szExeFile[MAX_PATH]: Executable name (just filename, not full path)
     * 
     * Note: MAX_PATH is typically 260 characters on Windows
     */
    PROCESSENTRY32 pe32;
    
    // CRITICAL: You MUST set dwSize before calling Process32First()
    // This is the same versioning pattern we saw with MEMORYSTATUSEX
    // The function uses this to verify structure compatibility
    pe32.dwSize = sizeof(PROCESSENTRY32);
    
    /* -------------------------------------------------------------------------
     * STEP 3: Get the first process with Process32First()
     * -------------------------------------------------------------------------
     * Process32First() initializes the iteration and gets the first process.
     * You must call this before Process32Next().
     * 
     * Parameters:
     * - hSnapshot: Handle from CreateToolhelp32Snapshot()
     * - lppe: Pointer to PROCESSENTRY32 to receive data
     * 
     * Returns: TRUE if a process was found, FALSE if no processes or error
     */
    if (Process32First(hSnapshot, &pe32)) {
        
        /* ---------------------------------------------------------------------
         * STEP 4: Loop through all processes with Process32Next()
         * ---------------------------------------------------------------------
         * We use a do-while loop because Process32First() already got the first
         * process. Each call to Process32Next() gets the next process.
         * 
         * Process32Next() returns FALSE when there are no more processes.
         */
        do {
            // Create a ProcessInfo instance to hold this process's data
            ProcessInfo info;
            
            // Copy the process ID from the PROCESSENTRY32 structure
            info.pid = pe32.th32ProcessID;
            
            /* -----------------------------------------------------------------
             * Get the process name from szExeFile
             * -----------------------------------------------------------------
             * szExeFile is a char array (CHAR[MAX_PATH]) containing the 
             * executable name. Because we're using the ANSI version of the
             * functions (not UNICODE), this is already a narrow char string.
             * 
             * Note: This is just the filename (e.g., "notepad.exe"), 
             * not the full path. To get the full path, you'd need to 
             * open the process and call GetModuleFileNameEx().
             */
            info.name = pe32.szExeFile;  // std::string can be assigned from char*
            
            /* -----------------------------------------------------------------
             * STEP 5: Try to get memory usage for this process
             * -----------------------------------------------------------------
             * To get memory info, we need to:
             * 1. Open a handle to the process with OpenProcess()
             * 2. Call GetProcessMemoryInfo() with that handle
             * 3. Close the handle when done
             * 
             * This might fail for system processes or processes owned by
             * other users (access denied), so we handle that gracefully.
             */
            
            // Initialize memory usage to 0 (will stay 0 if we can't access the process)
            info.memoryUsage = 0;
            
            /* -----------------------------------------------------------------
             * OpenProcess() - Get a handle to an existing process
             * -----------------------------------------------------------------
             * Parameters:
             * - dwDesiredAccess: What permissions we need. We're requesting:
             *   - PROCESS_QUERY_INFORMATION: Allows querying process info
             *   - PROCESS_VM_READ: Allows reading process memory info
             *   (These are combined with bitwise OR operator '|')
             * - bInheritHandle: FALSE = child processes won't inherit this handle
             * - dwProcessId: PID of the process to open
             * 
             * Returns: Handle to the process, or NULL if access denied or invalid PID
             */
            HANDLE hProcess = OpenProcess(
                PROCESS_QUERY_INFORMATION | PROCESS_VM_READ,  // Access flags
                FALSE,                                         // Don't inherit handle
                pe32.th32ProcessID                            // Process ID to open
            );
            
            // Check if we successfully opened the process
            if (hProcess != NULL) {
                
                /* -------------------------------------------------------------
                 * GetProcessMemoryInfo() - Get memory usage statistics
                 * -------------------------------------------------------------
                 * This function fills a PROCESS_MEMORY_COUNTERS structure with
                 * various memory metrics.
                 * 
                 * PROCESS_MEMORY_COUNTERS fields:
                 * - cb: Size of structure (should be set to sizeof())
                 * - PageFaultCount: Number of page faults
                 * - PeakWorkingSetSize: Maximum working set size
                 * - WorkingSetSize: Current working set size (RAM in use)
                 * - QuotaPeakPagedPoolUsage: Peak paged pool usage
                 * - QuotaPagedPoolUsage: Current paged pool usage
                 * - QuotaPeakNonPagedPoolUsage: Peak non-paged pool
                 * - QuotaNonPagedPoolUsage: Current non-paged pool
                 * - PagefileUsage: Current page file (virtual memory) usage
                 * - PeakPagefileUsage: Peak page file usage
                 * 
                 * "Working Set" = The portion of a process's memory that is 
                 * currently resident in physical RAM (not swapped to disk).
                 */
                PROCESS_MEMORY_COUNTERS pmc;
                
                // Call GetProcessMemoryInfo() - returns TRUE on success
                if (GetProcessMemoryInfo(hProcess, &pmc, sizeof(pmc))) {
                    // Success - save the working set size
                    // WorkingSetSize is the amount of physical RAM used
                    info.memoryUsage = pmc.WorkingSetSize;
                }
                // If GetProcessMemoryInfo fails, info.memoryUsage stays 0
                
                /* -------------------------------------------------------------
                 * CloseHandle() - Release the process handle
                 * -------------------------------------------------------------
                 * ALWAYS close handles when you're done with them!
                 * Failing to close handles causes "handle leaks" which can
                 * eventually exhaust system resources.
                 * 
                 * Windows tracks handles per-process. Each process has a 
                 * handle table, and there's a limit (typically 16 million
                 * handles, but practical limits are lower).
                 */
                CloseHandle(hProcess);
            }
            // If OpenProcess failed (returned NULL), we skip memory info for this process
            // This commonly happens for System processes and other users' processes
            
            // Add this process to our vector
            // push_back() adds an element to the end of the vector
            // The vector automatically grows to accommodate new elements
            processes.push_back(info);
            
        } while (Process32Next(hSnapshot, &pe32));
        // Loop continues until Process32Next returns FALSE (no more processes)
    }
    
    /* -------------------------------------------------------------------------
     * STEP 6: Clean up - close the snapshot handle
     * -------------------------------------------------------------------------
     * The snapshot is a system resource that must be released.
     * CloseHandle() frees the memory used by the snapshot.
     */
    CloseHandle(hSnapshot);
    
    // Return the vector containing all processes
    // C++ will use move semantics, so this is efficient (no deep copy)
    return processes;
}


/* =============================================================================
 * SECTION 6: FUNCTION - DisplayProcesses()
 * =============================================================================
 * This function gets all running processes and displays them in a formatted
 * table. It demonstrates C++ stream formatting with iomanip.
 */

/**
 * DisplayProcesses - Prints a formatted table of all running processes
 * 
 * This function:
 * 1. Calls GetRunningProcesses() to get the process list
 * 2. Prints a header row with column labels
 * 3. Iterates through processes and prints each one
 * 4. Shows a total count at the end
 * 
 * The output uses fixed-width columns for alignment using std::setw().
 */
void DisplayProcesses() {
    
    // Print section header
    std::cout << "\n========== RUNNING PROCESSES ==========\n\n";
    
    // Get all running processes by calling our enumeration function
    // 'auto' lets the compiler infer the type (std::vector<ProcessInfo>)
    auto processes = GetRunningProcesses();
    
    /* -------------------------------------------------------------------------
     * Print the table header row
     * -------------------------------------------------------------------------
     * We use iostream manipulators from <iomanip> for formatting:
     * - std::left: Left-align text within the field width
     * - std::setw(n): Set the next field to be n characters wide
     * 
     * The width only applies to the NEXT item inserted into the stream,
     * so we need to repeat std::setw() for each column.
     */
    std::cout << std::left                // Left-align all following output
              << std::setw(8) << "PID"    // Column 1: PID (8 chars wide)
              << std::setw(40) << "Process Name"   // Column 2: Name (40 chars)
              << std::setw(15) << "Memory (MB)"    // Column 3: Memory (15 chars)
              << "\n";
    
    // Print a separator line using the string constructor
    // std::string(63, '-') creates a string of 63 dash characters
    std::cout << std::string(63, '-') << "\n";
    
    /* -------------------------------------------------------------------------
     * Print each process
     * -------------------------------------------------------------------------
     * Range-based for loop iterates through each ProcessInfo in the vector.
     * 'const auto&' means:
     * - const: We won't modify the process info
     * - auto: Compiler infers type (ProcessInfo)
     * - &: Use reference to avoid copying the struct
     */
    for (const auto& proc : processes) {
        std::cout << std::left                 // Left-align
                  << std::setw(8) << proc.pid  // Process ID
                  << std::setw(40) << proc.name // Process name
                  << std::setw(15) 
                  << std::fixed                // Use fixed-point notation (not scientific)
                  << std::setprecision(2)      // Show 2 decimal places
                  // Convert bytes to megabytes: divide by (1024 * 1024)
                  // 1024 bytes = 1 KB, 1024 KB = 1 MB
                  // Using 1024.0 (with decimal) forces floating-point division
                  << (proc.memoryUsage / (1024.0 * 1024.0))
                  << "\n";
    }
    
    // Print the total count
    // .size() returns the number of elements in the vector
    std::cout << "\nTotal Processes: " << processes.size() << "\n";
}


/* =============================================================================
 * SECTION 7: MAIN FUNCTION - Program Entry Point
 * =============================================================================
 * Every C++ program must have a main() function. This is where execution begins
 * when the program starts.
 */

/**
 * main - Program entry point
 * 
 * The operating system calls this function when the program starts.
 * 
 * Returns:
 * - 0 indicates successful execution (EXIT_SUCCESS)
 * - Non-zero indicates an error occurred (EXIT_FAILURE)
 * 
 * The return value is passed back to the calling process (usually the shell)
 * and can be checked with %ERRORLEVEL% in batch files or $? in PowerShell.
 */
int main() {
    
    // Print program title
    std::cout << "Windows System Information Tool\n";
    std::cout << "================================\n";
    
    // Call our functions to display system info and processes
    DisplaySystemInfo();   // Shows CPU, memory, computer name, username
    DisplayProcesses();    // Shows list of all running processes
    
    // Wait for user input before closing the console window
    // This prevents the window from closing immediately when run by double-click
    std::cout << "\nPress Enter to exit...";
    std::cin.get();  // Waits for user to press Enter
    
    // Return 0 to indicate successful execution
    return 0;
}

/* =============================================================================
 * END OF FILE
 * =============================================================================
 * 
 * FURTHER LEARNING:
 * 
 * 1. Windows API Documentation (official):
 *    https://docs.microsoft.com/en-us/windows/win32/api/
 * 
 * 2. Process and Thread Functions:
 *    https://docs.microsoft.com/en-us/windows/win32/procthread/
 * 
 * 3. Tool Help Library:
 *    https://docs.microsoft.com/en-us/windows/win32/toolhelp/
 * 
 * 4. PSAPI (Process Status API):
 *    https://docs.microsoft.com/en-us/windows/win32/psapi/
 * 
 * EXERCISES:
 * 
 * 1. Add a function to get the full path of each process executable
 *    (Hint: Use GetModuleFileNameEx() after opening the process)
 * 
 * 2. Add CPU usage per process
 *    (Hint: Use GetProcessTimes() and calculate delta over time)
 * 
 * 3. Filter to only show processes using more than X MB of memory
 * 
 * 4. Sort processes by memory usage (descending)
 *    (Hint: Use std::sort with a custom comparator)
 * 
 * =============================================================================
 */
