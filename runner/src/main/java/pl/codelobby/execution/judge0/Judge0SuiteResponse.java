package pl.codelobby.execution.judge0;

import java.util.List;

public record Judge0SuiteResponse(String protocol, List<CaseResult> results, Benchmark benchmark) {
    public record Benchmark(String status, int warmupRuns, int measuredRuns,
                            Double medianTime, Double peakMemory, String measurementScope, String measurementTrust, Double sandboxCpuTime) { }
    public record CaseResult(
            String status,
            String stdout,
            String stderr,
            Double time,
            Double memory,
            String message
    ) {
    }
}
