#include <iostream>
#include <string>
using namespace std;

// O(n^3) worst-case time, O(1) auxiliary space.
int longestPalindrome(const string& s) {
    int best = 0;
    for (int left = 0; left < (int)s.size(); ++left) {
        for (int right = left; right < (int)s.size(); ++right) {
            bool palindrome = true;
            for (int a = left, b = right; a < b; ++a, --b) {
                if (s[a] != s[b]) { palindrome = false; break; }
            }
            if (palindrome && right - left + 1 > best) best = right - left + 1;
        }
    }
    return best;
}
