package pl.codelobby.execution.api;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public record TrustedRunRequest(
        @NotBlank @Size(max=128) String requestId,
        @NotBlank @Size(max=128) String taskVersionId,
        @NotNull TrustedEvaluationRequest.Language language,
        @NotNull TrustedEvaluationRequest.Mode mode,
        @Size(max=64) String adapterId,
        @NotNull @Size(min=1,max=20) List<@NotNull @Valid SourceFile> sourceFiles,
        @NotNull @Size(max=100000) String stdin,
        @NotNull @Valid TrustedEvaluationRequest.Limits limits
) {
    public TrustedRunRequest(String id, TrustedEvaluationRequest.Language language, String source,
            String stdin, TrustedEvaluationRequest.Limits limits, TrustedEvaluationRequest.Mode mode, String adapter) {
        this(id,"local-demo-v1",language,mode==null?TrustedEvaluationRequest.Mode.FULL_PROGRAM:mode,adapter,
                List.of(new SourceFile(SourceFile.entryName(language),source)),stdin,limits);
    }
}
