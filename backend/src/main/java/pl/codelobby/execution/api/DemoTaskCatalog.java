package pl.codelobby.execution.api;

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Random;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import pl.codelobby.execution.config.Judge0Properties;

@Component
public class DemoTaskCatalog {

    private final Judge0Properties judge0Properties;

    private static final String TASK_ID = "longest-palindrome";
    private static final String STARTER_CODE = """
            #include <iostream>
            #include <string>
            using namespace std;

            int longestPalindrome(const string& text) {
                // Zwróć długość najdłuższego spójnego palindromu.
                return 0;
            }
            """;

    // PoC fixture. The CodeLobby backend will supply these trusted cases later.
    private static final List<TrustedEvaluationRequest.TestCase> TEST_CASES = palindromeCases();

    private static List<TrustedEvaluationRequest.TestCase> palindromeCases() {
        List<String> inputs = new ArrayList<>(List.of("babad", "cbbd", "",
                "a", "ab", "aa", "aba", "abba", "abcde", "racecar", "aaaaaaa",
                "abacdfgdcaba", "abcddcbae", "eabcddcba"));
        Random random = new Random(20261009L);
        for (int length : new int[]{64, 128, 256, 512, 1024, 1536}) {
            inputs.add("a".repeat(length));
            inputs.add("ab".repeat(length / 2));
            inputs.add("abc".repeat(length / 3) + "a".repeat(length % 3));
            StringBuilder text = new StringBuilder(length);
            for (int i = 0; i < length; i++) text.append((char) ('a' + random.nextInt(26)));
            inputs.add(text.toString());
            String half = text.substring(0, length / 2);
            inputs.add(half + new StringBuilder(half).reverse());
            inputs.add(half + "z" + new StringBuilder(half).reverse());
        }
        List<TrustedEvaluationRequest.TestCase> cases = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            String input = inputs.get(i);
            cases.add(new TrustedEvaluationRequest.TestCase(input + "\n", longestPalindromeLength(input) + "\n",
                    i < 3 ? TrustedEvaluationRequest.Visibility.PUBLIC : TrustedEvaluationRequest.Visibility.HIDDEN));
        }
        return List.copyOf(cases);
    }

    // Trusted fixture oracle, independent of the supplied Manacher example.
    private static int longestPalindromeLength(String text) {
        int best = 0;
        for (int center = 0; center < text.length(); center++) {
            for (int even = 0; even <= 1; even++) {
                int left = center, right = center + even;
                while (left >= 0 && right < text.length() && text.charAt(left) == text.charAt(right)) {
                    best = Math.max(best, right - left + 1);
                    left--;
                    right++;
                }
            }
        }
        return best;
    }

    public DemoTaskCatalog(Judge0Properties judge0Properties) {
        this.judge0Properties = judge0Properties;
    }

    public PublicTask publicTask(String taskId) {
        requireKnownTask(taskId);
        List<PublicTestCase> publicCases = TEST_CASES.stream()
                .filter(testCase -> testCase.visibility() == TrustedEvaluationRequest.Visibility.PUBLIC)
                .map(testCase -> new PublicTestCase(testCase.input(), testCase.expectedOutput()))
                .toList();
        return new PublicTask(TASK_ID, "Najdłuższy palindrom",
                "Uzupełnij funkcję w kodzie startowym wybranego języka. Zwróć długość najdłuższego spójnego palindromu. Napis zawiera 0–1537 małych liter a–z; dla pustego napisu zwróć 0. Nie dodawaj main(), klasy Main ani wypisywania wyników. Run przyjmuje jeden napis. Submit: 50 testów w jednym procesie, następnie 2 rozgrzewki i 5 pomiarów; limity dotyczą całego zestawu.",
                STARTER_CODE, publicCases, java.util.Map.of(
                        "CPP", STARTER_CODE,
                        "PYTHON", "def longest_palindrome(text: str) -> int:\n    # Zwróć długość najdłuższego palindromu.\n    return 0\n",
                        "JAVA", "static int longestPalindrome(String text) {\n    // Zwróć długość najdłuższego palindromu.\n    return 0;\n}\n"));
    }

    public TrustedEvaluationRequest trustedSubmission(StudentSubmissionRequest request) {
        requireKnownTask(request.taskId());
        var limits = new TrustedEvaluationRequest.Limits(
                Math.round(judge0Properties.cpuTimeLimitSeconds() * 1000),
                Math.round(judge0Properties.wallTimeLimitSeconds() * 1000),
                judge0Properties.memoryLimitKilobytes() * 1000L);
        return new TrustedEvaluationRequest(UUID.randomUUID().toString(),
                request.language(), request.sourceCode(), limits, TEST_CASES,
                TrustedEvaluationRequest.Mode.FUNCTION, "palindrome-length-v1");
    }

    private void requireKnownTask(String taskId) {
        if (!TASK_ID.equals(taskId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nieznane zadanie");
        }
    }

    public record PublicTask(String id, String title, String description, String starterCode,
                             List<PublicTestCase> publicTestCases, java.util.Map<String, String> starterCodes) {
    }

    public record PublicTestCase(String input, String expectedOutput) {
    }
}
