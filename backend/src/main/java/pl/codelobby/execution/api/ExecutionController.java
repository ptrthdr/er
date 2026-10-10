package pl.codelobby.execution.api;

import jakarta.validation.Valid;
import pl.codelobby.execution.service.ExecutionService;
import pl.codelobby.execution.api.BenchmarkResponse;
import pl.codelobby.execution.api.SubmissionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@RequestMapping("/api")
@ConditionalOnProperty(name = "codelobby.demo.enabled", havingValue = "true")
public class ExecutionController {

    private final ExecutionService executionService;
    private final DemoTaskCatalog demoTaskCatalog;
    private final String engine;
    private final pl.codelobby.execution.service.SubmissionJobs jobs;

    public ExecutionController(ExecutionService executionService,
                               DemoTaskCatalog demoTaskCatalog,
                               pl.codelobby.execution.service.SubmissionJobs jobs,
                               @Value("${codelobby.execution.engine:piston}") String engine) {
        this.executionService = executionService;
        this.demoTaskCatalog = demoTaskCatalog;
        this.engine = engine;
        this.jobs = jobs;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        String compiler = "judge0".equalsIgnoreCase(engine) ? "C++ · Judge0 language 54" : "GCC " + executionService.cppVersion();
        return new HealthResponse("UP", engine, compiler);
    }

    @GetMapping("/tasks/{taskId}")
    public DemoTaskCatalog.PublicTask task(@PathVariable String taskId) {
        return demoTaskCatalog.publicTask(taskId);
    }

    @PostMapping("/executions")
    public ExecutionResponse execute(@Valid @RequestBody ExecutionRequest request) {
        return executionService.execute(new ExecutionRequest(
                pl.codelobby.execution.service.CppAdapters.resolve(TrustedEvaluationRequest.Mode.FUNCTION,
                        "palindrome-length-v1").wrap(request.sourceCode(), false, request.language()), request.stdin(), request.language()));
    }

    @PostMapping("/benchmarks")
    public BenchmarkResponse benchmark(@Valid @RequestBody ExecutionRequest request) {
        return executionService.benchmark(request);
    }

    @PostMapping("/submissions")
    public SubmissionResponse submit(@Valid @RequestBody StudentSubmissionRequest request) {
        return executionService.submit(demoTaskCatalog.trustedSubmission(request));
    }

    @PostMapping("/submission-jobs")
    public org.springframework.http.ResponseEntity<pl.codelobby.execution.service.SubmissionJobs.Snapshot> enqueue(
            @Valid @RequestBody StudentSubmissionRequest request) {
        return org.springframework.http.ResponseEntity.accepted().body(jobs.enqueue(demoTaskCatalog.trustedSubmission(request)));
    }

    @GetMapping("/submission-jobs/{id}")
    public pl.codelobby.execution.service.SubmissionJobs.Snapshot job(@PathVariable String id) {
        return jobs.get(id);
    }

    public record HealthResponse(String status, String engine, String compiler) {
    }
}
