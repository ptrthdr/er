package pl.codelobby.execution.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.codelobby.execution.service.ExecutionService;

/** Server-to-server API. InternalApiKeyFilter authenticates every request. */
@RestController
@RequestMapping("/internal/v1")
public class InternalExecutionController {

    private final ExecutionService executionService;
    private final pl.codelobby.execution.service.SubmissionJobs jobs;

    public InternalExecutionController(ExecutionService executionService, pl.codelobby.execution.service.SubmissionJobs jobs) {
        this.executionService = executionService;
        this.jobs = jobs;
    }

    @PostMapping("/runs")
    public RunResult run(@Valid @RequestBody TrustedRunRequest request) {
        return new RunResult(request.requestId(), executionService.runTrusted(request));
    }

    @PostMapping("/evaluations")
    public EvaluationResult evaluate(@Valid @RequestBody TrustedEvaluationRequest request) {
        return new EvaluationResult(request.submissionId(), executionService.submit(request));
    }

    @PostMapping("/evaluation-jobs")
    public org.springframework.http.ResponseEntity<pl.codelobby.execution.service.SubmissionJobs.Snapshot> enqueue(
            @Valid @RequestBody TrustedEvaluationRequest request) {
        return org.springframework.http.ResponseEntity.accepted().body(jobs.enqueue(request));
    }

    @GetMapping("/evaluation-jobs/{id}")
    public pl.codelobby.execution.service.SubmissionJobs.Snapshot job(
            @org.springframework.web.bind.annotation.PathVariable String id) {
        return jobs.get(id);
    }

    public record RunResult(String requestId, ExecutionResponse result) {
    }

    @GetMapping("/capabilities")
    public java.util.Map<String, Object> capabilities() {
        return java.util.Map.of("apiVersion", "v1", "languages", java.util.List.of("CPP", "PYTHON", "JAVA"),
                "modes", java.util.List.of("FULL_PROGRAM", "FUNCTION"), "maxTestCases", 50,
                "adapters", pl.codelobby.execution.service.CppAdapters.DEFINITIONS,
                "modeProfiles", java.util.Map.of(
                        "FULL_PROGRAM", java.util.Map.of("limitScope", "PER_TEST", "measurementScope", "SUITE_SUM_OF_PROCESSES", "sourceShape", "COMPLETE_PROGRAM"),
                        "FUNCTION", java.util.Map.of("limitScope", "WHOLE_SUITE", "measurementScope", "FUNCTION_CALLS_ELAPSED", "sourceShape", "ADAPTER_LANGUAGE_PROFILE")),
                "languageProfiles", java.util.Map.of("CPP", "GCC 9.2.0", "PYTHON", "Python 3.8.1", "JAVA", "OpenJDK 13.0.1"));
    }

    public record EvaluationResult(String submissionId, SubmissionResponse result) {
    }
}
