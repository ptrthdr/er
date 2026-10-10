#include <algorithm>
#include <iostream>
#include <string>
#include <vector>
using namespace std;

// Manacher: O(n) time, O(n) auxiliary space.
int longestPalindrome(const string& s) {
    int n = (int)s.size(), best = 0;
    vector<int> odd(n), even(n);
    for (int i = 0, left = 0, right = -1; i < n; ++i) {
        int k = i > right ? 1 : min(odd[left + right - i], right - i + 1);
        while (i - k >= 0 && i + k < n && s[i - k] == s[i + k]) ++k;
        odd[i] = k--;
        best = max(best, 2 * odd[i] - 1);
        if (i + k > right) { left = i - k; right = i + k; }
    }
    for (int i = 0, left = 0, right = -1; i < n; ++i) {
        int k = i > right ? 0 : min(even[left + right - i + 1], right - i + 1);
        while (i - k - 1 >= 0 && i + k < n && s[i - k - 1] == s[i + k]) ++k;
        even[i] = k--;
        best = max(best, 2 * even[i]);
        if (i + k > right) { left = i - k - 1; right = i + k; }
    }
    return best;
}
