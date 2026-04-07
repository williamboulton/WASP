/**
 * Drive letter assignment from GetDiskData loop (system_metrics.cpp ~479-487).
 * Production first does root.resize(2) (e.g. "C:\\" -> "C:"), then this ternary runs.
 * The ternary only treats root[1]=='\\' as the "C\\" two-char case (letter + backslash).
 *
 * Build:
 *   cl /EHsc /W4 /std:c++17 /Fe:build\test_drive_letter_from_root.exe tests\cpp\test_drive_letter_from_root.cpp
 */

#include <cstdio>
#include <string>

/* --- copied from system_metrics.cpp --- */
static std::string DriveLetterFromRoot(const std::string& root) {
    return (root.size() >= 2 && root[1] == '\\') ? root.substr(0, 1) : root;
}

static bool expect_eq(const char* name, const std::string& got, const std::string& want) {
    if (got != want) {
        std::fprintf(stderr, "FAIL %s: got \"%s\" want \"%s\"\n", name, got.c_str(), want.c_str());
        return false;
    }
    return true;
}

int main() {
    /* After resize(2) in the tool, roots look like "C:" — second char is ':', not '\\'. */
    if (!expect_eq("c_colon", DriveLetterFromRoot("C:"), "C:")) return 1;
    /* Two-char form: letter then backslash (what the ternary checks for). */
    if (!expect_eq("c_letter_backslash", DriveLetterFromRoot(std::string("C\\")), "C")) return 1;
    if (!expect_eq("d_letter_backslash", DriveLetterFromRoot(std::string("D\\")), "D")) return 1;
    /* "C:\\" is three chars (C : \\); root[1] is ':', so the whole string is returned. */
    if (!expect_eq("c_colon_backslash_three_chars", DriveLetterFromRoot("C:\\"), "C:\\")) return 1;
    if (!expect_eq("short", DriveLetterFromRoot("C"), "C")) return 1;
    return 0;
}
