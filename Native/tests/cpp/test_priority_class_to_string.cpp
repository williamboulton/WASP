/**
 * Isolated copy of PriorityClassToString from system_metrics.cpp (lines ~526-536).
 * Error-prone: must stay in sync with Windows priority class constants and JSON labels.
 *
 * Build:
 *   cl /EHsc /W4 /std:c++17 /Fe:build\test_priority_class_to_string.exe tests\cpp\test_priority_class_to_string.cpp
 */

#define WIN32_LEAN_AND_MEAN
#include <windows.h>

#include <cstdio>
#include <string>

/* --- copied from system_metrics.cpp --- */
static std::string PriorityClassToString(DWORD pc) {
    switch (pc) {
        case REALTIME_PRIORITY_CLASS: return "REALTIME";
        case HIGH_PRIORITY_CLASS: return "HIGH";
        case ABOVE_NORMAL_PRIORITY_CLASS: return "ABOVE_NORMAL";
        case NORMAL_PRIORITY_CLASS: return "NORMAL";
        case BELOW_NORMAL_PRIORITY_CLASS: return "BELOW_NORMAL";
        case IDLE_PRIORITY_CLASS: return "IDLE";
        default: return "UNKNOWN";
    }
}

static bool expect_eq(const char* name, const std::string& got, const char* want) {
    if (got != want) {
        std::fprintf(stderr, "FAIL %s: got \"%s\" want \"%s\"\n", name, got.c_str(), want);
        return false;
    }
    return true;
}

int main() {
    if (!expect_eq("REALTIME", PriorityClassToString(REALTIME_PRIORITY_CLASS), "REALTIME")) return 1;
    if (!expect_eq("HIGH", PriorityClassToString(HIGH_PRIORITY_CLASS), "HIGH")) return 1;
    if (!expect_eq("ABOVE_NORMAL", PriorityClassToString(ABOVE_NORMAL_PRIORITY_CLASS), "ABOVE_NORMAL")) return 1;
    if (!expect_eq("NORMAL", PriorityClassToString(NORMAL_PRIORITY_CLASS), "NORMAL")) return 1;
    if (!expect_eq("BELOW_NORMAL", PriorityClassToString(BELOW_NORMAL_PRIORITY_CLASS), "BELOW_NORMAL")) return 1;
    if (!expect_eq("IDLE", PriorityClassToString(IDLE_PRIORITY_CLASS), "IDLE")) return 1;
    if (!expect_eq("unknown_0", PriorityClassToString(0), "UNKNOWN")) return 1;
    if (!expect_eq("unknown_dead", PriorityClassToString(0xDEADBEEFu), "UNKNOWN")) return 1;
    return 0;
}
