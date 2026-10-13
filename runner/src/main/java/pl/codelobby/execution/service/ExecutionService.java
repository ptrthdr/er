package pl.codelobby.execution.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.codelobby.execution.api.ExecutionRequest;
import pl.codelobby.execution.api.ExecutionResponse;
import pl.codelobby.execution.api.BenchmarkResponse;
import pl.codelobby.execution.api.TrustedEvaluationRequest;
import pl.codelobby.execution.api.TrustedRunRequest;
import pl.codelobby.execution.api.SubmissionResponse;
import pl.codelobby.execution.config.PistonProperties;
import pl.codelobby.execution.judge0.Judge0Client;
import pl.codelobby.execution.judge0.Judge0Response;
import pl.codelobby.execution.judge0.Judge0SuiteResponse;
import pl.codelobby.execution.piston.PistonClient;
import pl.codelobby.execution.piston.PistonResponse;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ExecutionService {

    private static final String SUITE_RESULT_PREFIX = "CODELOBBY_TEST_SUITE_RESULT_V1:";
    private final PistonClient pistonClient;
    private final PistonProperties properties;
    private final Judge0Client judge0Client;
    private final ObjectMapper objectMapper;
    private final String engine;

    public ExecutionService(PistonClient pistonClient, PistonProperties properties,
                            Judge0Client judge0Client, ObjectMapper objectMapper,
                            @Value("${codelobby.execution.engine:piston}") String engine) {
        this.pistonClient = pistonClient;
        this.properties = properties;
        this.judge0Client = judge0Client;
        this.objectMapper = objectMapper;
        this.engine = engine;
    }

    private final java.util.concurrent.locks.ReentrantLock sandboxLock = new java.util.concurrent.locks.ReentrantLock(true);

    private <T> T withSandbox(java.util.function.Supplier<T> action) {
        boolean acquired = false;
        try {
            acquired = sandboxLock.tryLock(2, java.util.concurrent.TimeUnit.SECONDS);
            if (!acquired) throw new ExecutionEngineUnavailableException("Sandbox zajęty; ponów żądanie później", null);
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExecutionEngineUnavailableException("Przerwano oczekiwanie na sandbox", e);
        } finally {
            if (acquired) sandboxLock.unlock();
        }
    }

    public void validateSubmission(TrustedEvaluationRequest request) {
        if (request.mode() == TrustedEvaluationRequest.Mode.PALINDROME_FUNCTION)
            throw new InvalidEvaluationException("Starszy tryb nie należy do kontraktu v1");
        if (!"judge0".equalsIgnoreCase(engine))
            throw new ExecutionEngineUnavailableException("Submit wymaga silnika Judge0", null);
        SourceBundle.prepare(request.sourceFiles(), request.language(), request.mode());
        judge0Client.validateLimits(request.limits());
        for (var test : request.testCases()) {
            SourceBundle.validateInputBytes(test.input());
            SourceBundle.validateInputBytes(test.expectedOutput());
        }
        var adapter = CppAdapters.resolve(request.mode(), request.adapterId());
        if (adapter != null) {
            if (!adapter.supportedLanguages().contains(request.language()))
                throw new InvalidEvaluationException("Język nie jest obsługiwany przez adapter");
            for (var test : request.testCases()) {
                adapter.validateInput(test.input());
                if (!test.expectedOutput().matches("[0-9]+\\n?"))
                    throw new InvalidEvaluationException("Adapter wymaga liczbowej odpowiedzi");
            }
        }
    }

    public SubmissionResponse submitQueued(TrustedEvaluationRequest request, Runnable onStarted) {
        boolean acquired = false;
        try {
            sandboxLock.lockInterruptibly();
            acquired = true;
            onStarted.run();
            return submitLocked(request);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExecutionEngineUnavailableException("Przerwano zadanie przy zatrzymaniu modułu", e);
        } finally {
            if (acquired) sandboxLock.unlock();
        }
    }

    public ExecutionResponse execute(ExecutionRequest request) { return withSandbox(() -> executeLocked(request)); }
    public BenchmarkResponse benchmark(ExecutionRequest request) { return withSandbox(() -> benchmarkLocked(request)); }
    public SubmissionResponse submit(TrustedEvaluationRequest request) { return withSandbox(() -> submitLocked(request)); }
    public ExecutionResponse runTrusted(TrustedRunRequest request) { return withSandbox(() -> runTrustedLocked(request)); }

    private ExecutionResponse executeLocked(ExecutionRequest request) {
        if ("judge0".equalsIgnoreCase(engine)) {
            return executeWithJudge0(request);
        }
        if (!"piston".equalsIgnoreCase(engine)) {
            throw new ExecutionEngineUnavailableException("Nieznany silnik wykonawczy: " + engine, null);
        }
        if (request.language() != TrustedEvaluationRequest.Language.CPP)
            throw new InvalidEvaluationException("Python i Java wymagają silnika Judge0");
        final PistonResponse response;
        try {
            response = pistonClient.execute(request.sourceCode(), request.stdin());
        } catch (ResourceAccessException exception) {
            throw new ExecutionEngineUnavailableException("Nie można połączyć się z Piston", exception);
        } catch (RestClientResponseException exception) {
            String detail = exception.getResponseBodyAsString();
            throw new ExecutionEngineUnavailableException(
                    "Piston zwrócił HTTP " + exception.getStatusCode().value()
                            + (detail.isBlank() ? "" : ": " + detail),
                    exception
            );
        } catch (RestClientException exception) {
            throw new ExecutionEngineUnavailableException("Nie udało się odczytać odpowiedzi Piston", exception);
        }

        PistonResponse.Stage compile = response.compile();
        PistonResponse.Stage run = response.run();
        boolean compilationFailed = compile != null && compile.code() != null && compile.code() != 0;
        PistonResponse.Stage measuredStage = compilationFailed ? compile : run;
        String status = mapStatus(compile, run, compilationFailed);
        String compileOutput = compile == null ? null : nonEmpty(safe(compile.stdout()) + safe(compile.stderr()));
        String message = compilationFailed
                ? compile.message()
                : run.message();

        return new ExecutionResponse(
                status,
                safe(run.stdout()),
                compilationFailed ? "" : safe(run.stderr()),
                compileOutput,
                compilationFailed ? null : run.cpu_time(),
                compilationFailed ? null : run.memory(),
                run.code(),
                measuredStage.status(),
                message
        );
    }

    private BenchmarkResponse benchmarkLocked(ExecutionRequest request) {
        if ("piston".equalsIgnoreCase(engine)) {
            return benchmarkPiston(request);
        }
        if ("judge0".equalsIgnoreCase(engine)) {
            return benchmarkJudge0(request);
        }
        throw new ExecutionEngineUnavailableException("Nieznany silnik wykonawczy: " + engine, null);
    }

    public SubmissionResponse runPublicTests(TrustedEvaluationRequest request) {
        if (request.mode() != TrustedEvaluationRequest.Mode.FUNCTION
                || request.testCases().stream().anyMatch(t -> t.visibility() != TrustedEvaluationRequest.Visibility.PUBLIC))
            throw new InvalidEvaluationException("Run testów wymaga funkcji i wyłącznie testów jawnych");
        return withSandbox(() -> submitLocked(request, false));
    }
    private SubmissionResponse submitLocked(TrustedEvaluationRequest request) {
        return submitLocked(request, true);
    }
    private SubmissionResponse submitLocked(TrustedEvaluationRequest request, boolean benchmarkEnabled) {
        validateSubmission(request);
        if (!"judge0".equalsIgnoreCase(engine)) {
            throw new ExecutionEngineUnavailableException(
                    "Ocena zestawu testów jest obecnie dostępna tylko w wariancie Judge0", null);
        }

        final Judge0Response response;
        try {
            var adapter = CppAdapters.resolve(request.mode(), request.adapterId());
            boolean batch = adapter != null;
            if (batch) {
                for (var test : request.testCases()) {
                    adapter.validateInput(test.input());
                    if (!test.expectedOutput().matches("[0-9]+\\n?"))
                        throw new InvalidEvaluationException("Adapter wymaga liczbowej odpowiedzi");
                }
            }
            var bundle = SourceBundle.prepare(request.sourceFiles(), request.language(), request.mode());
            response = !batch
                ? judge0Client.executeSuiteFiles(bundle, request.testCases(), request.limits(), request.language())
                : benchmarkEnabled
                    ? judge0Client.executeSuite(adapter.wrap(bundle.source(), true, request.language()), request.testCases(), request.limits(), true, request.language())
                    : judge0Client.executeSuite(adapter.wrap(bundle.source(), true, request.language()), request.testCases(), request.limits(), true, request.language(), false);
        } catch (ResourceAccessException exception) {
            throw new ExecutionEngineUnavailableException("Nie można połączyć się z Judge0", exception);
        } catch (RestClientResponseException exception) {
            String detail = exception.getResponseBodyAsString();
            throw new ExecutionEngineUnavailableException("Judge0 zwrócił HTTP " + exception.getStatusCode().value()
                    + (detail.isBlank() ? "" : ": " + detail), exception);
        } catch (RestClientException exception) {
            throw new ExecutionEngineUnavailableException("Nie udało się odczytać odpowiedzi Judge0", exception);
        }

        String judgeStatus = response.status() == null ? "Unknown" : response.status().description();
        if ("Compilation Error".equals(judgeStatus)) {
            return new SubmissionResponse("COMPILATION_ERROR", 0, request.testCases().size(), null,
                    null, nonEmpty(response.compile_output()), List.of(), judgeStatus, response.message(), null);
        }
        if (!"Accepted".equals(judgeStatus) || response.stdout() == null
                || !response.stdout().startsWith(SUITE_RESULT_PREFIX)) {
            throw new ExecutionEngineUnavailableException("Judge0 nie zwrócił wyniku zestawu testów", null);
        }

        final Judge0SuiteResponse suite;
        try {
            suite = objectMapper.readValue(response.stdout().substring(SUITE_RESULT_PREFIX.length()),
                    Judge0SuiteResponse.class);
        } catch (JsonProcessingException exception) {
            throw new ExecutionEngineUnavailableException("Judge0 zwrócił nieprawidłowy wynik zestawu testów", exception);
        }
        if (suite == null || !"codelobby-suite-v1".equals(suite.protocol()) || suite.results() == null
                || suite.results().size() != request.testCases().size()
                || suite.results().stream().anyMatch(result -> result == null || result.status() == null)) {
            throw new ExecutionEngineUnavailableException("Judge0 zwrócił niepełny wynik zestawu testów", null);
        }

        List<SubmissionResponse.TestResult> results = new ArrayList<>();
        int passed = 0;
        long runtimeTotal = 0;
        long peakMemory = 0;
        String failureStatus = null;
        String message = null;
        for (int i = 0; i < request.testCases().size(); i++) {
            TrustedEvaluationRequest.TestCase testCase = request.testCases().get(i);
            Judge0SuiteResponse.CaseResult run = suite.results().get(i);
            String caseStatus = mapJudge0Status(run.status(), run.message());
            if ("ACCEPTED".equals(caseStatus)) {
                boolean matches = normalizeOutput(testCase.expectedOutput())
                        .equals(normalizeOutput(run.stdout()));
                caseStatus = matches ? "ACCEPTED" : "WRONG_ANSWER";
            }

            Long caseRuntime = run.time() == null ? null : Math.round(run.time() * 1000.0);
            Long caseMemory = run.memory() == null ? null : Math.round(run.memory() * 1000.0);
            if (caseRuntime != null) runtimeTotal += caseRuntime;
            if (caseMemory != null) peakMemory = Math.max(peakMemory, caseMemory);
            if ("ACCEPTED".equals(caseStatus)) passed++;
            else if (failureStatus == null || ("WRONG_ANSWER".equals(failureStatus)
                    && !"WRONG_ANSWER".equals(caseStatus))) failureStatus = caseStatus;
            if (message == null && run.message() != null)
                message = testCase.visibility() == TrustedEvaluationRequest.Visibility.HIDDEN
                    ? "Niepowodzenie testu ukrytego" : run.message();

            results.add(new SubmissionResponse.TestResult(i + 1, caseStatus, caseRuntime, caseMemory,
                    testCase.visibility() == TrustedEvaluationRequest.Visibility.HIDDEN ? null : safe(run.stdout()),
                    testCase.visibility() == TrustedEvaluationRequest.Visibility.HIDDEN ? null : safe(run.stderr()),
                    testCase.visibility().name(),
                    testCase.visibility() == TrustedEvaluationRequest.Visibility.PUBLIC ? testCase.expectedOutput() : null));
        }

        String finalStatus = passed == request.testCases().size()
                ? "ACCEPTED" : failureStatus == null ? "WRONG_ANSWER" : failureStatus;
        // The outer Judge0 status only says that the suite protocol completed.
        // It is not the verdict for the student's code and would mislead the UI.
        SubmissionResponse.BenchmarkMetrics benchmark = null;
        if (benchmarkEnabled && "ACCEPTED".equals(finalStatus)) {
            var measured = suite.benchmark();
            if (measured == null) {
                throw new ExecutionEngineUnavailableException("Worker Judge0 nie obsługuje benchmarku Submit", null);
            }
            if ("COMPLETED".equals(measured.status()) && (measured.warmupRuns() != 2
                    || measured.measuredRuns() != 5 || measured.medianTime() == null
                    || measured.peakMemory() == null || !Double.isFinite(measured.medianTime())
                    || !Double.isFinite(measured.peakMemory()) || measured.medianTime() < 0
                    || measured.peakMemory() < 0)) {
                throw new ExecutionEngineUnavailableException("Judge0 zwrócił niepełny benchmark", null);
            }
            benchmark = new SubmissionResponse.BenchmarkMetrics(measured.status(), measured.warmupRuns(),
                    measured.measuredRuns(), measured.medianTime() == null ? null : measured.medianTime() * 1000.0,
                    measured.peakMemory() == null ? null : Math.round(measured.peakMemory() * 1000.0),
                    measured.measurementScope() == null ? "SUITE_SUM_OF_PROCESSES" : measured.measurementScope(),
                    measured.measurementTrust() == null ? "SANDBOX" : measured.measurementTrust(),
                    measured.sandboxCpuTime() == null ? null : measured.sandboxCpuTime() * 1000.0);
        }
        boolean measured = benchmark != null && "COMPLETED".equals(benchmark.status());
        return new SubmissionResponse(finalStatus, passed, request.testCases().size(),
                measured ? Math.round(benchmark.medianRuntimeMs()) : benchmarkEnabled ? null : runtimeTotal,
                measured ? benchmark.peakMemoryBytes() : benchmarkEnabled ? null : peakMemory,
                nonEmpty(response.compile_output()), results, null, message, benchmark);
    }

    private ExecutionResponse runTrustedLocked(TrustedRunRequest request) {
        if (request.mode() == TrustedEvaluationRequest.Mode.PALINDROME_FUNCTION)
            throw new InvalidEvaluationException("Starszy tryb nie należy do kontraktu v1");
        if (!"judge0".equalsIgnoreCase(engine)) {
            throw new ExecutionEngineUnavailableException("Zaufane API wymaga aktywnego Judge0", null);
        }
        judge0Client.validateLimits(request.limits());
        SourceBundle.validateInputBytes(request.stdin());
        var bundle = SourceBundle.prepare(request.sourceFiles(), request.language(), request.mode());
        var adapter = CppAdapters.resolve(request.mode(), request.adapterId());
        if (adapter == null) {
            try {
                var response = judge0Client.executeFiles(bundle, request.stdin(), request.limits(), request.language());
                return mapRunResponse(response);
            } catch (RestClientException e) { throw new ExecutionEngineUnavailableException("Nie można wykonać kodu w Judge0", e); }
        }
        if (adapter != null) adapter.validateInput(request.stdin());
        return executeWithJudge0(new ExecutionRequest(adapter == null ? bundle.source()
                : adapter.wrap(bundle.source(), false, request.language()), request.stdin(), request.language()), request.limits());
    }

    private BenchmarkResponse benchmarkPiston(ExecutionRequest request) {
        final int warmups = 2;
        final int measuredRuns = 5;
        List<Long> runtimes = new ArrayList<>(measuredRuns);
        List<Long> memories = new ArrayList<>(measuredRuns);
        ExecutionResponse last = null;

        for (int i = 0; i < warmups + measuredRuns; i++) {
            last = execute(request);
            if (!"COMPLETED".equals(last.status())) {
                return benchmarkFailure(last, warmups, measuredRuns);
            }
            if (i >= warmups) {
                if (last.executionTimeMs() != null) runtimes.add(last.executionTimeMs());
                if (last.memoryBytes() != null) memories.add(last.memoryBytes());
            }
        }

        return new BenchmarkResponse(
                last.status(), last.stdout(), last.stderr(), last.compileOutput(),
                median(runtimes), memories.stream().max(Comparator.naturalOrder()).orElse(null),
                warmups, measuredRuns, last.exitCode(), last.engineStatus(), last.message()
        );
    }

    private BenchmarkResponse benchmarkJudge0(ExecutionRequest request) {
        final int warmups = 2;
        final int measuredRuns = 5;
        final Judge0Response response;
        try {
            response = judge0Client.execute(request.sourceCode(), request.stdin(), warmups + measuredRuns, request.language());
        } catch (ResourceAccessException exception) {
            throw new ExecutionEngineUnavailableException("Nie można połączyć się z Judge0", exception);
        } catch (RestClientResponseException exception) {
            String detail = exception.getResponseBodyAsString();
            throw new ExecutionEngineUnavailableException("Judge0 zwrócił HTTP " + exception.getStatusCode().value()
                    + (detail.isBlank() ? "" : ": " + detail), exception);
        } catch (RestClientException exception) {
            throw new ExecutionEngineUnavailableException("Nie udało się odczytać odpowiedzi Judge0", exception);
        }

        String description = response.status() == null ? "Unknown" : response.status().description();
        String status = mapJudge0Status(description, response.message());
        Long runtime = response.time() == null ? null : Math.round(response.time() * 1000.0);
        Long memory = response.memory() == null ? null : Math.round(response.memory() * 1000.0);
        return new BenchmarkResponse(status, safe(response.stdout()), safe(response.stderr()),
                nonEmpty(response.compile_output()), runtime, memory, warmups, measuredRuns,
                response.exit_code(), description, response.message());
    }

    private static BenchmarkResponse benchmarkFailure(ExecutionResponse result, int warmups, int measuredRuns) {
        return new BenchmarkResponse(result.status(), result.stdout(), result.stderr(), result.compileOutput(),
                result.executionTimeMs(), result.memoryBytes(), warmups, measuredRuns,
                result.exitCode(), result.engineStatus(), result.message());
    }

    static Long median(List<Long> values) {
        if (values.isEmpty()) return null;
        List<Long> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0
                ? Math.round(sorted.get(middle - 1) / 2.0 + sorted.get(middle) / 2.0)
                : sorted.get(middle);
    }

    public String cppVersion() {
        return properties.cppVersion();
    }

    private ExecutionResponse executeWithJudge0(ExecutionRequest request) {
        return executeWithJudge0(request, null);
    }

    private ExecutionResponse executeWithJudge0(ExecutionRequest request,
                                               TrustedEvaluationRequest.Limits limits) {
        final Judge0Response response;
        try {
            response = judge0Client.execute(request.sourceCode(), request.stdin(), limits, request.language());
        } catch (ResourceAccessException exception) {
            throw new ExecutionEngineUnavailableException("Nie można połączyć się z Judge0", exception);
        } catch (RestClientResponseException exception) {
            String detail = exception.getResponseBodyAsString();
            throw new ExecutionEngineUnavailableException("Judge0 zwrócił HTTP " + exception.getStatusCode().value()
                    + (detail.isBlank() ? "" : ": " + detail), exception);
        } catch (RestClientException exception) {
            throw new ExecutionEngineUnavailableException("Nie udało się odczytać odpowiedzi Judge0", exception);
        }

        return mapRunResponse(response);
    }

    private ExecutionResponse mapRunResponse(Judge0Response response) {
        String description = response.status() == null ? "Unknown" : response.status().description();
        String status = mapJudge0Status(description, response.message());
        if ("EXECUTION_ERROR".equals(status)) throw new ExecutionEngineUnavailableException("Błąd infrastruktury Judge0", null);
        String compileOutput = nonEmpty(response.compile_output());
        return new ExecutionResponse(
                status,
                safe(response.stdout()),
                safe(response.stderr()),
                compileOutput,
                response.time() == null ? null : Math.round(response.time() * 1000.0),
                response.memory() == null ? null : Math.round(response.memory() * 1000.0),
                response.exit_code(),
                description,
                response.message()
        );
    }

    private static String mapJudge0Status(String description, String message) {
        if (message != null && message.startsWith("Output limit exceeded")) return "OUTPUT_LIMIT_EXCEEDED";
        if (message != null && message.startsWith("Memory limit exceeded")) return "MEMORY_LIMIT_EXCEEDED";
        return switch (description) {
            case "Accepted" -> "ACCEPTED";
            case "Wrong Answer" -> "WRONG_ANSWER";
            case "Compilation Error" -> "COMPILATION_ERROR";
            case "Time Limit Exceeded" -> "TIME_LIMIT_EXCEEDED";
            case "Memory Limit Exceeded" -> "MEMORY_LIMIT_EXCEEDED";
            case "Output Limit Exceeded" -> "OUTPUT_LIMIT_EXCEEDED";
            case "Internal Error" -> "EXECUTION_ERROR";
            default -> description.startsWith("Runtime Error") ? "RUNTIME_ERROR" : "EXECUTION_ERROR";
        };
    }

    private String mapStatus(PistonResponse.Stage compile, PistonResponse.Stage run, boolean compilationFailed) {
        if (compilationFailed) {
            return "COMPILATION_ERROR";
        }
        if ("TO".equals(run.status())) {
            return "TIME_LIMIT_EXCEEDED";
        }
        // Piston reports cgroup memory kills as exit code 137/RE. Require a
        // near-limit measurement to avoid classifying every SIGKILL as an MLE.
        if (Integer.valueOf(137).equals(run.code())
                && run.memory() != null
                && run.memory() >= Math.floor(properties.runMemoryLimitBytes() * 0.90)) {
            return "MEMORY_LIMIT_EXCEEDED";
        }
        if (run.code() != null && run.code() == 0 && run.status() == null) {
            return "COMPLETED";
        }
        return "RUNTIME_ERROR";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String nonEmpty(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String normalizeOutput(String value) {
        return safe(value).replace("\r\n", "\n").replace("\r", "\n").stripTrailing();
    }
}
