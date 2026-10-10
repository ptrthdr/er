package pl.codelobby.execution.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StudentSubmissionRequest(
        @NotBlank(message = "Identyfikator zadania jest wymagany")
        @Size(max = 64, message = "Identyfikator zadania jest za długi")
        String taskId,

        @NotBlank(message = "Kod C++ nie może być pusty")
        @Size(max = 50_000, message = "Kod może mieć maksymalnie 50 000 znaków")
        String sourceCode,
        TrustedEvaluationRequest.Language language
) {
    public StudentSubmissionRequest { if (language == null) language = TrustedEvaluationRequest.Language.CPP; }
}
