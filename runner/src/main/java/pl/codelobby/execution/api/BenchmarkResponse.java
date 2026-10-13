package pl.codelobby.execution.api;

public record BenchmarkResponse(
        String status,
        String stdout,
        String stderr,
        String compileOutput,
        Long medianRuntimeMs,
        Long peakMemoryBytes,
        int warmupRuns,
        int measuredRuns,
        Integer exitCode,
        String engineStatus,
        String message
) {
}
