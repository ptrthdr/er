package pl.codelobby.execution.api;

import java.util.List;

public record SubmissionResponse(
        String status,
        int passedTests,
        int totalTests,
        Long runtimeMs,
        Long peakMemoryBytes,
        String compileOutput,
        List<TestResult> testResults,
        String engineStatus,
        String message,
        BenchmarkMetrics benchmark
) {
    public record BenchmarkMetrics(String status, int warmupRuns, int measuredRuns,
                                   Double medianRuntimeMs, Long peakMemoryBytes, String measurementScope,
                                   String measurementTrust, Double sandboxCpuTimeMs) {
        public BenchmarkMetrics(String status, int warmupRuns, int measuredRuns, Double medianRuntimeMs, Long peakMemoryBytes) {
            this(status, warmupRuns, measuredRuns, medianRuntimeMs, peakMemoryBytes, "SUITE_PROCESS_CPU", "SANDBOX", null);
        }
    }
    public record TestResult(
            int testNumber,
            String status,
            Long runtimeMs,
            Long peakMemoryBytes,
            String output,
            String stderr,
            String visibility,
            String expectedOutput
    ) {
        public TestResult(int testNumber, String status, Long runtimeMs, Long peakMemoryBytes, String output, String stderr) {
            this(testNumber, status, runtimeMs, peakMemoryBytes, output, stderr, null, null);
        }
    }
}
