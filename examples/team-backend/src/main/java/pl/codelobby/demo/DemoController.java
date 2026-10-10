package pl.codelobby.demo;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Fixed task fixture belongs to this mock main backend, not the execution engine. */
@RestController
@RequestMapping("/team-demo")
public class DemoController {
    private final EngineClient engine;
    private final HashSet<String> jobs = new HashSet<>();
    private static final Map<String, Long> LIMITS = Map.of("cpuTimeMs", 2000L, "wallTimeMs", 5000L, "memoryLimitBytes", 128000000L);
    private static final List<Map<String, String>> TESTS = List.of(
            Map.of("input", "hello\n", "expectedOutput", "2\n", "visibility", "PUBLIC"),
            Map.of("input", "aeiou\n", "expectedOutput", "5\n", "visibility", "HIDDEN"),
            Map.of("input", "rhythm\n", "expectedOutput", "0\n", "visibility", "HIDDEN"),
            Map.of("input", "\n", "expectedOutput", "0\n", "visibility", "HIDDEN"));
    public DemoController(EngineClient engine) { this.engine = engine; }
    public record Code(@NotBlank @Size(max=50000) String sourceCode) { }
    public record Run(@NotBlank @Size(max=50000) String sourceCode, @Size(max=100000) String stdin) { }

    @GetMapping("/task")
    public Map<String, Object> task() {
        return Map.of("id", "team-vowels", "title", "Policz samogłoski", "allowedLanguages", List.of("CPP"),
                "starterCodes", Map.of("CPP", "int countVowels(const string& text) { return 0; }"),
                "publicTestCases", List.of(TESTS.getFirst()));
    }
    @PostMapping("/run")
    public JsonNode run(@Valid @RequestBody Run input) {
        var response = engine.post("/internal/v1/runs", Map.of("requestId", UUID.randomUUID().toString(),
                "language", "CPP", "mode", "FUNCTION", "adapterId", "vowel-count-v1",
                "sourceCode", input.sourceCode(), "stdin", input.stdin() == null ? "" : input.stdin(), "limits", LIMITS));
        return response.getBody().get("result");
    }
    @PostMapping("/submissions")
    public synchronized ResponseEntity<JsonNode> submit(@Valid @RequestBody Code input) {
        if (jobs.size() >= 100) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demo retains at most 100 submission IDs; restart demo to reset");
        String id = UUID.randomUUID().toString();
        var response = engine.post("/internal/v1/evaluation-jobs", Map.of("submissionId", id,
                "language", "CPP", "mode", "FUNCTION", "adapterId", "vowel-count-v1",
                "sourceCode", input.sourceCode(), "testCases", TESTS, "limits", LIMITS));
        jobs.add(id);
        return ResponseEntity.accepted().body(response.getBody());
    }
    @GetMapping("/submissions/{id}")
    public JsonNode result(@PathVariable String id) {
        synchronized (this) {
            if (!jobs.contains(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown local submission");
        }
        return engine.getJob(id);
    }
}
