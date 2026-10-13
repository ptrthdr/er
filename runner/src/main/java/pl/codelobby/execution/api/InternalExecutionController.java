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
    private final pl.codelobby.execution.judge0.Judge0Client judge0;
    private final pl.codelobby.execution.service.SubmissionJobs jobs;

    public InternalExecutionController(ExecutionService executionService, pl.codelobby.execution.service.SubmissionJobs jobs, pl.codelobby.execution.judge0.Judge0Client judge0) {
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
    public org.springframework.http.ResponseEntity<pl.codelobby.execution.service.SubmissionJobs.Snapshot> enqueue(
            @Valid @RequestBody TrustedEvaluationRequest request) {
        var job = jobs.enqueue(request);
        return org.springframework.http.ResponseEntity.status(
                job.state() == pl.codelobby.execution.service.SubmissionJobs.State.FINISHED ||
                job.state() == pl.codelobby.execution.service.SubmissionJobs.State.FAILED ? 200 : 202).body(job);
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
        var settings = judge0.configuration();
        var result = new java.util.LinkedHashMap<String,Object>();
        result.put("apiVersion","v1");
        result.put("languages",java.util.List.of("JAVA","PYTHON","CPP"));
        result.put("modes",java.util.List.of("FULL_PROGRAM","FUNCTION"));
        result.put("files",java.util.Map.of("multiFile",true,"supportsDirectories",false,"maxFiles",20,
            "maxSourceBytes",131072,"maxHttpBodyBytes",2000000,"maxFileNameLength",120));
        result.put("entryPoints",java.util.Map.of("JAVA","Main","PYTHON","main.py","CPP","main.cpp"));
        result.put("maxTestCases",50); result.put("maxOutputBytesPerStream",65536);
        result.put("limits",java.util.Map.of("maxCpuTimeMs",Math.round(settings.cpuTimeLimitSeconds()*1000),
            "maxWallTimeMs",Math.round(settings.wallTimeLimitSeconds()*1000),"maxMemoryLimitBytes",settings.memoryLimitKilobytes()*1000L));
        result.put("runtimeVersions",judge0.runtimeVersions());
        result.put("cppStandard","c++17"); result.put("cppCompilerFlags",java.util.List.of("-std=c++17","-O2"));
        result.put("adapters",pl.codelobby.execution.service.CppAdapters.DEFINITIONS);
        result.put("modeProfiles",java.util.Map.of(
            "FULL_PROGRAM",java.util.Map.of("limitScope","PER_TEST","measurementScope","SUITE_SUM_OF_PROCESSES"),
            "FUNCTION",java.util.Map.of("limitScope","WHOLE_SUITE","measurementScope","FUNCTION_CALLS_ELAPSED","maxFiles",1)));
        return result;
    }

    public record EvaluationResult(String submissionId, SubmissionResponse result) {
    }
}
