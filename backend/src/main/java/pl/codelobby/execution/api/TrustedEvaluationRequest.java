package pl.codelobby.execution.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Input supplied by the trusted CodeLobby backend, never by a student browser. */
public record TrustedEvaluationRequest(
        @NotBlank @Size(max = 128) String submissionId,
        @NotNull Language language,
        @NotBlank @Size(max = 50_000) String sourceCode,
        @NotNull @Valid Limits limits,
        @NotNull @Size(min = 1, max = 50) List<@NotNull @Valid TestCase> testCases,
        Mode mode,
        @Size(max = 64) String adapterId
) {
    public enum Mode { FULL_PROGRAM, FUNCTION, PALINDROME_FUNCTION }
    public enum Language { CPP, PYTHON, JAVA }

    public enum Visibility { PUBLIC, HIDDEN }

    public record Limits(
            @Min(1) long cpuTimeMs,
            @Min(1) long wallTimeMs,
            @Min(1_000) long memoryLimitBytes
    ) {
    }

    public record TestCase(
            @NotNull @Size(max = 100_000) String input,
            @NotNull @Size(max = 100_000) String expectedOutput,
            @NotNull Visibility visibility
    ) {
    }
}
