package pl.codelobby.execution.service;

/** Shared server-owned template for allowlisted string-to-integer functions. */
public final class CppLineFunctionHarness {
    private CppLineFunctionHarness() { }

    static String wrap(String source, boolean batch, String functionName) {
        return "#include <iostream>\n#include <string>\n#include <vector>\n#include <algorithm>\n#include <limits>\nusing namespace std;\n" + source + "\n"
                + (batch ? """
                    int main() {
                        int count;
                        if (!(cin >> count)) return 1;
                        cin.ignore(numeric_limits<streamsize>::max(), '\\n');
                        for (int i = 0; i < count; ++i) {
                            string text;
                            if (!getline(cin, text)) return 1;
                            cout << longestPalindrome(text) << '\\n';
                        }
                    }
                    """ : """
                    int main() {
                        string text;
                        getline(cin, text);
                        cout << longestPalindrome(text) << '\\n';
                    }
                    """).replace("longestPalindrome(text)", functionName + "(text)");
    }
}
