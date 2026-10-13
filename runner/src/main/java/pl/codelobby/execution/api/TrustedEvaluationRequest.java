package pl.codelobby.execution.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** Complete, trusted task snapshot supplied by the main backend. */
public record TrustedEvaluationRequest(
        @NotBlank @Size(max=128) @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:-]{0,127}") String submissionId,
        @NotBlank @Size(max=128) String taskVersionId,
        @NotNull Language language,
        @NotNull Mode mode,
        @Size(max=64) String adapterId,
        @NotNull @Size(min=1,max=20) List<@NotNull @Valid SourceFile> sourceFiles,
        @NotNull @Valid Limits limits,
        @NotNull @Size(min=1,max=50) List<@NotNull @Valid TestCase> testCases
) {
    // Java-only convenience for existing demo and service tests, not an HTTP alias.
    public TrustedEvaluationRequest(String id, Language language, String source, Limits limits,
                                    List<TestCase> tests, Mode mode, String adapterId) {
        this(id,"local-demo-v1",language,mode==null?Mode.FULL_PROGRAM:mode,adapterId,
                List.of(new SourceFile(SourceFile.entryName(language),source)),limits,tests);
    }
    public enum Mode { FULL_PROGRAM, FUNCTION, PALINDROME_FUNCTION }
    public enum Language { CPP, PYTHON, JAVA }
    public enum Visibility { PUBLIC, HIDDEN }
    public record Limits(@Min(1) long cpuTimeMs, @Min(1) long wallTimeMs,
                         @Min(1000) long memoryLimitBytes) {}
    public record TestCase(@NotNull @Size(max=100000) String input,
                           @NotNull @Size(max=100000) String expectedOutput,
                           @NotNull Visibility visibility) {}
}
