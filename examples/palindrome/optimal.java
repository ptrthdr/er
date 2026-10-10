static int longestPalindrome(String text) {
    char[] s = new char[text.length() * 2 + 1];
    for (int i = 0; i < s.length; i++) s[i] = i % 2 == 0 ? '#' : text.charAt(i / 2);
    int[] radius = new int[s.length];
    int center = 0, right = 0, best = 0;
    for (int i = 0; i < s.length; i++) {
        if (i < right) radius[i] = Math.min(right - i, radius[2 * center - i]);
        while (i - radius[i] - 1 >= 0 && i + radius[i] + 1 < s.length
               && s[i - radius[i] - 1] == s[i + radius[i] + 1]) radius[i]++;
        if (i + radius[i] > right) { center = i; right = i + radius[i]; }
        best = Math.max(best, radius[i]);
    }
    return best;
}
