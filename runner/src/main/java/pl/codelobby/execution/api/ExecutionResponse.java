package pl.codelobby.execution.api;

public record ExecutionResponse(
        String status,
        String stdout,
        String stderr,
        String compileOutput,
        Long executionTimeMs,
        Long memoryBytes,
        Integer exitCode,
        String engineStatus,
        String message
) {
}
