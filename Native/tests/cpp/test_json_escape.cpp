/**
 * Isolated copy of JsonEscape from system_metrics.cpp (lines ~88-103).
 * Error-prone: escape order, control chars, \uXXXX for c < 32.
 *
 * Build (x64 Native Tools Command Prompt for VS):
 *   cl /EHsc /W4 /std:c++17 /Fe:build\test_json_escape.exe tests\cpp\test_json_escape.cpp
 */

#include <cstdio>
#include <string>

/* --- copied from system_metrics.cpp --- */
static std::string JsonEscape(const std::string& s) {
    std::string out;
    out.reserve(s.size() + 8);
    for (unsigned char c : s) {
        if (c == '"') out += "\\\"";
        else if (c == '\\') out += "\\\\";
        else if (c == '\b') out += "\\b";
        else if (c == '\f') out += "\\f";
        else if (c == '\n') out += "\\n";
        else if (c == '\r') out += "\\r";
        else if (c == '\t') out += "\\t";
        else if (c < 32) {
            char buf[8];
            sprintf_s(buf, "\\u%04x", c);
            out += buf;
        } else
            out += c;
    }
    return out;
}

static bool expect_eq(const char* name, const std::string& got, const std::string& want) {
    if (got != want) {
        std::fprintf(stderr, "FAIL %s:\n  got:  %s\n  want: %s\n", name, got.c_str(), want.c_str());
        return false;
    }
    return true;
}

int main() {
    if (!expect_eq("empty", JsonEscape(""), "")) return 1;
    if (!expect_eq("ascii", JsonEscape("hello"), "hello")) return 1;
    if (!expect_eq("quote", JsonEscape("\""), "\\\"")) return 1;
    if (!expect_eq("backslash", JsonEscape("\\"), "\\\\")) return 1;
    if (!expect_eq("mixed", JsonEscape("a\"b\\c"), "a\\\"b\\\\c")) return 1;
    if (!expect_eq("newline", JsonEscape("x\ny"), "x\\ny")) return 1;
    if (!expect_eq("tab", JsonEscape("\t"), "\\t")) return 1;
    if (!expect_eq("bs", JsonEscape("\b"), "\\b")) return 1;
    if (!expect_eq("ff", JsonEscape("\f"), "\\f")) return 1;
    if (!expect_eq("cr", JsonEscape("\r"), "\\r")) return 1;
    /* control SUB (0x1a) -> \u001a */
    std::string sub;
    sub += static_cast<char>(0x1a);
    if (!expect_eq("control_1a", JsonEscape(sub), "\\u001a")) return 1;
    if (!expect_eq("nul", JsonEscape(std::string(1, '\0')), "\\u0000")) return 1;
    return 0;
}
