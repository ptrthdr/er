def longest_palindrome(text: str) -> int:
    transformed = "#" + "#".join(text) + "#"
    radius = [0] * len(transformed)
    center = right = best = 0
    for i in range(len(transformed)):
        if i < right:
            radius[i] = min(right - i, radius[2 * center - i])
        while (i - radius[i] - 1 >= 0 and i + radius[i] + 1 < len(transformed)
               and transformed[i - radius[i] - 1] == transformed[i + radius[i] + 1]):
            radius[i] += 1
        if i + radius[i] > right:
            center, right = i, i + radius[i]
        best = max(best, radius[i])
    return best
