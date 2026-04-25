/**
 * Per-core usage line from GetPerCoreUsage (system_metrics.cpp ~334-337).
 * Error-prone: same idle/total ratio as aggregate CPU but per-core dTotal/dIdle.
 *
 * Build:
 *   cl /EHsc /W4 /std:c++17 /Fe:build\test_per_core_usage_percent.exe tests\cpp\test_per_core_usage_percent.cpp
 */

#include <cmath>
#include <cstdint>
#include <cstdio>

static double PerCoreUsagePercent(std::uint64_t dIdle, std::uint64_t dTotal) {
    if (dTotal > 0) return 100.0 * (1.0 - static_cast<double>(dIdle) / static_cast<double>(dTotal));
    return 0.0;
}

static bool near_eq(double a, double b) { return std::fabs(a - b) < 1e-9; }

static bool expect_near(const char* name, double got, double want) {
    if (!near_eq(got, want)) {
        std::fprintf(stderr, "FAIL %s: got %.12g want %.12g\n", name, got, want);
        return false;
    }
    return true;
}

int main() {
    if (!expect_near("dTotal_zero", PerCoreUsagePercent(5, 0), 0.0)) return 1;
    if (!expect_near("half", PerCoreUsagePercent(50, 100), 50.0)) return 1;
    if (!expect_near("all_idle", PerCoreUsagePercent(100, 100), 0.0)) return 1;
    if (!expect_near("no_idle", PerCoreUsagePercent(0, 100), 100.0)) return 1;
    return 0;
}
