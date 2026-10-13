package pl.codelobby.execution.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExecutionRequest(
        @NotBlank(message = "Kod C++ nie może być pusty")
        @Size(max = 50_000, message = "Kod może mieć maksymalnie 50 000 znaków")
        String sourceCode,

        @Size(max = 100_000, message = "Wejście może mieć maksymalnie 100 000 znaków")
        String stdin,
        TrustedEvaluationRequest.Language language
) {
    public ExecutionRequest { if (language == null) language = TrustedEvaluationRequest.Language.CPP; }
    public ExecutionRequest(String sourceCode, String stdin) { this(sourceCode, stdin, TrustedEvaluationRequest.Language.CPP); }
}
