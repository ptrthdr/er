package pl.codelobby.execution.service;

import java.util.List;
import java.util.Map;
import pl.codelobby.execution.api.TrustedEvaluationRequest.Language;
import pl.codelobby.execution.api.TrustedEvaluationRequest.Mode;

/** Allowlisted, server-owned adapters. IDs are versioned and cannot contain code. */
public final class CppAdapters {
    public record LanguageProfile(String functionName, String signature, String starterCode, String sourceShape) { }
    private CppAdapters() { }
    public record Definition(String id, String signature, String inputFormat,
                             String outputFormat, String limitScope, String functionName,
                             String measurementScope, List<Language> supportedLanguages,
                             Map<Language, LanguageProfile> languageProfiles) {
        public Definition(String id, String signature, String inputFormat,
                          String outputFormat, String limitScope, String functionName) {
            this(id, signature, inputFormat, outputFormat, limitScope, functionName,
                    "FUNCTION_CALLS_ELAPSED", List.of(Language.CPP, Language.PYTHON, Language.JAVA),
                    profiles(functionName, signature));
        }
        public String wrap(String source, boolean batch) {
            return CppLineFunctionHarness.wrap(source, batch, functionName);
        }
        public String wrap(String source, boolean batch, pl.codelobby.execution.api.TrustedEvaluationRequest.Language language) {
            return LanguageHarness.wrap(source, batch, functionName, language);
        }
        public void validateInput(String input) {
            if (!input.matches("[a-z]*\\n?"))
                throw new InvalidEvaluationException("Adapter " + id + " wymaga jednego wiersza małych liter a–z");
        }
    }
    private static Map<Language, LanguageProfile> profiles(String function, String cppSignature) {
        String pythonName = switch (function) {
            case "longestPalindrome" -> "longest_palindrome";
            case "countVowels" -> "count_vowels";
            default -> throw new IllegalArgumentException("Missing language profile: " + function);
        };
        String javaSignature = "static int " + function + "(String text)";
        return Map.of(
                Language.CPP, new LanguageProfile(function, cppSignature,
                        cppSignature + " {\n    // TODO\n    return 0;\n}\n", "FUNCTION_WITHOUT_MAIN"),
                Language.PYTHON, new LanguageProfile(pythonName, "def " + pythonName + "(text: str) -> int",
                        "def " + pythonName + "(text: str) -> int:\n    # TODO\n    return 0\n", "FUNCTION_WITHOUT_DRIVER"),
                Language.JAVA, new LanguageProfile(function, javaSignature,
                        javaSignature + " {\n    // TODO\n    return 0;\n}\n", "STATIC_METHOD_WITHOUT_CLASS"));
    }

    public static String functionFor(String cppFunction, Language language) {
        return DEFINITIONS.stream().filter(d -> d.functionName().equals(cppFunction))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown function"))
                .languageProfiles().get(language).functionName();
    }

    public static final List<Definition> DEFINITIONS = List.of(
            new Definition("palindrome-length-v1", "int longestPalindrome(const string& text)",
                    "one line, lowercase a-z; empty allowed", "nonnegative integer", "WHOLE_SUITE", "longestPalindrome"),
            new Definition("vowel-count-v1", "int countVowels(const string& text)",
                    "one line, lowercase a-z; empty allowed", "nonnegative integer", "WHOLE_SUITE", "countVowels"));

    public static Definition resolve(Mode mode, String id) {
        if (mode == Mode.PALINDROME_FUNCTION) {
            if (id != null && !id.equals("palindrome-length-v1"))
                throw new InvalidEvaluationException("Nieprawidłowy adapter dla starszego trybu palindromu");
            id = "palindrome-length-v1";
        } else if (mode == null || mode == Mode.FULL_PROGRAM) {
            if (id != null) throw new InvalidEvaluationException("FULL_PROGRAM nie przyjmuje adapterId");
            return null;
        }
        final String selected = id;
        return DEFINITIONS.stream().filter(d -> d.id().equals(selected)).findFirst()
                .orElseThrow(() -> new InvalidEvaluationException("Nieznany lub brakujący adapterId"));
    }
}
