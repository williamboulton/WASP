/**
 * Core formula from GetCpuUsagePercent (system_metrics.cpp ~170-194), without statics
 * or GetSystemTimes. Error-prone: divide-by-zero, first-sample zero, ordering.
 *
 * Build:
 *   cl /EHsc /W4 /std:c++17 /Fe:build\test_cpu_usage_percent_delta.exe tests\cpp\test_cpu_usage_percent_delta.cpp
 */

#include <cmath>
#include <cstdint>
#include <cstdio>

/* Same logic as the block inside GetCpuUsagePercent after FILETIME → idle/total. */
static double CpuUsagePercentFromDelta(std::uint64_t prevIdle, std::uint64_t prevTotal, std::uint64_t idle,
                                       std::uint64_t total) {
    double pct = 0.0;
    if (prevTotal != 0 && total > prevTotal) {
        std::uint64_t dTotal = total - prevTotal;
        std::uint64_t dIdle = idle - prevIdle;
        if (dTotal > 0) pct = 100.0 * (1.0 - static_cast<double>(dIdle) / static_cast<double>(dTotal));
    }
    return pct;
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
    /* First sample: prevTotal == 0 → 0% */
    if (!expect_near("first_sample", CpuUsagePercentFromDelta(0, 0, 100, 200), 0.0)) return 1;

    /* 50% busy: dIdle = half of dTotal */
    if (!expect_near("half_idle", CpuUsagePercentFromDelta(0, 1000, 250, 1500), 50.0)) return 1;

    /* dTotal == 0 (no wall clock advance) → 0% */
    if (!expect_near("zero_delta_total", CpuUsagePercentFromDelta(10, 100, 10, 100), 0.0)) return 1;

    /* 100% CPU (no idle growth) */
    if (!expect_near("full_cpu", CpuUsagePercentFromDelta(0, 100, 0, 200), 100.0)) return 1;

    /* 0% CPU (all idle growth) */
    if (!expect_near("all_idle", CpuUsagePercentFromDelta(0, 100, 100, 200), 0.0)) return 1;

    /* total did not increase vs prev (clock weirdness) → 0% */
    if (!expect_near("total_not_greater", CpuUsagePercentFromDelta(5, 100, 5, 100), 0.0)) return 1;

    return 0;
}
