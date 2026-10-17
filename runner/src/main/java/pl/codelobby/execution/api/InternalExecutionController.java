package pl.codelobby.execution.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import pl.codelobby.execution.judge0.Judge0Client;
import pl.codelobby.execution.service.CppAdapters;
import pl.codelobby.execution.service.ExecutionService;
import pl.codelobby.execution.service.SourceBundle;
import pl.codelobby.execution.service.SubmissionJobs;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-to-server API. InternalApiKeyFilter authenticates every request. */
@RestController
@RequestMapping("/internal/v1")
public class InternalExecutionController {

    private final ExecutionService executionService;
    private final Judge0Client judge0;
    private final SubmissionJobs jobs;

    public InternalExecutionController(ExecutionService executionService, SubmissionJobs jobs,
                                       Judge0Client judge0) {
        this.executionService = executionService;
        this.jobs = jobs;
        this.judge0 = judge0;
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
    public ResponseEntity<SubmissionJobs.Snapshot> enqueue(
            @Valid @RequestBody TrustedEvaluationRequest request
    ) {
        SubmissionJobs.Snapshot job = jobs.enqueue(request);
        boolean completed = job.state() == SubmissionJobs.State.FINISHED
                || job.state() == SubmissionJobs.State.FAILED;
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(job.submissionId()).toUri();

        return ResponseEntity.status(completed ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .location(location).body(job);
    }

    @GetMapping("/evaluation-jobs/{id}")
    public SubmissionJobs.Snapshot job(
            @PathVariable @Pattern(regexp = TrustedEvaluationRequest.SUBMISSION_ID_PATTERN) String id
    ) {
        return jobs.get(id);
    }

    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        var settings = judge0.configuration();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("apiVersion", "v1");
        result.put("languages", List.of("JAVA", "PYTHON", "CPP"));
        result.put("modes", List.of("FULL_PROGRAM", "FUNCTION"));
        result.put("files", Map.of("multiFile", true, "supportsDirectories", false,
                "maxFiles", SourceBundle.MAX_FILES, "maxSourceBytes", SourceBundle.MAX_SOURCE_BYTES,
                "maxHttpBodyBytes", SourceBundle.MAX_BODY_BYTES, "maxFileNameLength", 120));
        result.put("entryPoints", Map.of("JAVA", "Main", "PYTHON", "main.py", "CPP", "main.cpp"));
        result.put("maxTestCases", 50);
        result.put("maxOutputBytesPerStream", SourceBundle.MAX_OUTPUT_BYTES);
        result.put("limits", Map.of("maxCpuTimeMs", Math.round(settings.cpuTimeLimitSeconds() * 1000),
                "maxWallTimeMs", Math.round(settings.wallTimeLimitSeconds() * 1000),
                "maxMemoryLimitBytes", settings.memoryLimitKilobytes() * 1000L));
        result.put("runtimeVersions", judge0.runtimeVersions());
        result.put("cppStandard", "c++17");
        result.put("cppCompilerFlags", List.of("-std=c++17", "-O2"));
        result.put("adapters", CppAdapters.DEFINITIONS);
        result.put("modeProfiles", Map.of(
                "FULL_PROGRAM", Map.of("limitScope", "PER_TEST", "measurementScope", "SUITE_SUM_OF_PROCESSES"),
                "FUNCTION", Map.of("limitScope", "WHOLE_SUITE", "measurementScope", "FUNCTION_CALLS_ELAPSED", "maxFiles", 1)));
        return result;
    }

    public record RunResult(String requestId, ExecutionResponse result) {
    }

    public record EvaluationResult(String submissionId, SubmissionResponse result) {
    }
}
