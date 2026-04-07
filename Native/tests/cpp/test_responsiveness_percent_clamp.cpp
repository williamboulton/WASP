/**
 * Clamping logic from the end of GetSystemResponsivenessPercent (system_metrics.cpp ~249-258).
 * Error-prone: frac bounds, resp bounds, total <= 0 guard.
 *
 * Build:
 *   cl /EHsc /W4 /std:c++17 /Fe:build\test_responsiveness_percent_clamp.exe tests\cpp\test_responsiveness_percent_clamp.cpp
 */

#include <cmath>
#include <cstdio>
#include <cstdlib>

/* --- copied from system_metrics.cpp (long double → double at return) --- */
static double ResponsivenessPercentFromDelayAndTotal(long double total, long double delay) {
    if (total <= 0.0L) return 100.0;

    long double frac = delay / total;
    if (frac < 0.0L) frac = 0.0L;
    if (frac > 1.0L) frac = 1.0L;
    long double resp = (1.0L - frac) * 100.0L;
    if (resp < 0.0L) resp = 0.0L;
    if (resp > 100.0L) resp = 100.0L;
    return static_cast<double>(resp);
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
    if (!expect_near("total_zero", ResponsivenessPercentFromDelayAndTotal(0.0L, 5.0L), 100.0)) return 1;
    if (!expect_near("total_neg", ResponsivenessPercentFromDelayAndTotal(-1.0L, 0.0L), 100.0)) return 1;

    if (!expect_near("no_delay", ResponsivenessPercentFromDelayAndTotal(100.0L, 0.0L), 100.0)) return 1;
    if (!expect_near("half_delay", ResponsivenessPercentFromDelayAndTotal(100.0L, 50.0L), 50.0)) return 1;
    if (!expect_near("full_delay", ResponsivenessPercentFromDelayAndTotal(100.0L, 100.0L), 0.0)) return 1;

    /* frac clamp > 1 */
    if (!expect_near("delay_gt_total", ResponsivenessPercentFromDelayAndTotal(10.0L, 100.0L), 0.0)) return 1;
    /* frac clamp < 0 */
    if (!expect_near("negative_delay", ResponsivenessPercentFromDelayAndTotal(10.0L, -5.0L), 100.0)) return 1;

    return 0;
}
