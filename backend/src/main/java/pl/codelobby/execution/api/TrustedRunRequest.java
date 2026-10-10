package pl.codelobby.execution.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One custom-input run requested by the trusted CodeLobby backend. */
public record TrustedRunRequest(
        @NotBlank @Size(max = 128) String requestId,
        @NotNull TrustedEvaluationRequest.Language language,
        @NotBlank @Size(max = 50_000) String sourceCode,
        @NotNull @Size(max = 100_000) String stdin,
        @NotNull @Valid TrustedEvaluationRequest.Limits limits,
        TrustedEvaluationRequest.Mode mode,
        @Size(max = 64) String adapterId
) {
}
