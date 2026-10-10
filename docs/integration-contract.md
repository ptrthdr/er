# CodeLobby ↔ execution module: integration contract v1

The main CodeLobby backend owns tasks, test cases, student authorization, and submission IDs. The execution module owns compilation, sandbox execution, comparison, and resource measurement. **A browser must never call `/internal/v1/*` or send hidden tests.**

## Server configuration

- `EXECUTION_ENGINE=judge0` selects the currently supported trusted execution path.
- `CODELOBBY_INTERNAL_API_KEY` enables `/internal/v1/*`. It must be at least 32 characters, generated outside the repository and shared only between backend services. With no key, this API returns HTTP 503.
- Send the key in `X-CodeLobby-Internal-Key`. Missing or incorrect keys return HTTP 401.
- Keep the execution API on a private network; use TLS for traffic between hosts. Do not route `/internal/v1/*` through the student frontend.
- `CODELOBBY_DEMO_ENABLED=false` is the default. Set it to `true` only for the local `longest-palindrome` demo page. Demo routes are under `/api/*` and are not part of the team integration contract.

## Run with custom input

`POST /internal/v1/runs` with `Content-Type: application/json`:

```json
{
  "requestId": "run-123",
  "language": "CPP",
  "sourceCode": "#include <iostream>\nint main(){int n;std::cin>>n;std::cout<<n*2;}",
  "stdin": "4\n",
  "limits": { "cpuTimeMs": 1000, "wallTimeMs": 3000, "memoryLimitBytes": 64000000 }
}
```

The response is `{ "requestId": "run-123", "result": { ...ExecutionResponse } }`. `result` includes status, stdout, stderr, compileOutput, executionTimeMs, memoryBytes, exitCode, engineStatus, and message.

## Submit against trusted tests

`POST /internal/v1/evaluations` with `Content-Type: application/json`:

```json
{
  "submissionId": "submission-123",
  "language": "CPP",
  "sourceCode": "#include <iostream>\nint main(){int n;std::cin>>n;std::cout<<n*2;}",
  "limits": { "cpuTimeMs": 1000, "wallTimeMs": 3000, "memoryLimitBytes": 64000000 },
  "testCases": [
    { "input": "4\n", "expectedOutput": "8", "visibility": "PUBLIC" },
    { "input": "-3\n", "expectedOutput": "-6", "visibility": "HIDDEN" }
  ]
}
```

The response is `{ "submissionId": "submission-123", "result": { ...SubmissionResponse } }`. `result` includes status, passedTests, totalTests, runtimeMs, peakMemoryBytes, compileOutput, testResults, engineStatus, message, and benchmark. After all tests pass, the worker reuses the compiled artifact for two warmup rounds and five measured rounds of the same complete test suite. `runtimeMs` is the rounded median of the five suite CPU totals; `peakMemoryBytes` is the maximum per-process peak across measured rounds. `benchmark` contains status (`COMPLETED` or `FAILED`), warmupRuns, measuredRuns, fractional medianRuntimeMs and peakMemoryBytes. Failed evaluation skips benchmarking (`benchmark: null`). A failed benchmark retains the correctness verdict but leaves aggregate runtime/memory null and must not enter a ranking. Each testResults entry still reports its initial correctness run. Hidden output/stderr are null. Sandbox setup and compilation are excluded; process startup and the execution wrapper remain part of CPU runtime. These are process metrics, not isolated function timings.

`ACCEPTED`, `WRONG_ANSWER`, `COMPILATION_ERROR`, `RUNTIME_ERROR`, `TIME_LIMIT_EXCEEDED`, and `MEMORY_LIMIT_EXCEEDED` are evaluation outcomes in HTTP 200 responses. Invalid requests or limits return HTTP 400. A disabled internal API returns 503; Judge0 failures also return 503 with a different `error` code. A retry may execute the same submission again: the module currently echoes IDs but does not persist or deduplicate requests.

## Bounds and units

- Version 1 supports `language: "CPP"` and a complete compilable C++ program containing `main()`. Function-signature harnesses are a separate next step.
- A submission has 1–50 cases. Source is limited to 50,000 characters; each input and expected output to 100,000 characters.
- Optional `mode` is `FULL_PROGRAM` (default) or `FUNCTION`; FUNCTION requires `adapterId` (`palindrome-length-v1` or `vowel-count-v1`). `GET /internal/v1/capabilities` lists adapters and signatures. PALINDROME_FUNCTION is a compatibility alias only. Function mode has whole-suite resource limits and no per-case runtime/memory; see `backend-handoff.md` and `function-evaluation.md` for its output protocol, state semantics and deployment limitations.
- `cpuTimeMs` and `wallTimeMs` are milliseconds; `memoryLimitBytes` is decimal bytes. The configured local maxima are 2000 ms CPU, 5000 ms wall time, and 128,000,000 bytes. The wall limit must be at least the CPU limit. Higher requested limits are rejected, never forwarded to Judge0.
- The adapter fixes the Judge0 C++ language ID, stack limit, maximum process count, compiler settings, and network-off policy. Student clients cannot change them.
- Output comparison normalizes CRLF/CR to LF and ignores trailing whitespace. This policy must be agreed with task authors before grading tasks where whitespace matters.
- Compilation is performed once per Submit, including its automatic benchmark. Every case and repetition uses a fresh Isolate box restored from the pristine compiled artifact. The conservative synchronous polling budget is 120 seconds plus 400 times (per-case wall limit + one second preparation allowance). Upstream timeouts must accommodate the workload and queue waiting. The single worker reserves box IDs 800–849.

## Deployment boundary

This contract and access control are ready for team integration. The current Apple Silicon Judge0 image is an unofficial local experiment with a patched privileged worker. The suite patch currently reserves fixed Isolate box IDs for one worker; do not raise `COUNT` within that worker container without redesigning box allocation. Before admitting untrusted students on a real site, deploy a reviewed and pinned Judge0 build on a suitable Linux host, verify sandbox limits and network isolation there, load-test the queue, and keep Judge0 itself off the public network.

## Language-specific adapter metadata

`GET /internal/v1/capabilities` preserves the v1 fields and adds:

- Each adapter: `supportedLanguages`, `languageProfiles`, `measurementScope`.
- `languageProfiles[CPP|PYTHON|JAVA]`: `functionName`, `signature`, `starterCode`,
  `sourceShape`. The starter is an unfinished student template, not an oracle
  or reference solution. Store it with the task version if the task should
  keep a stable editor template.
- `modeProfiles`: explicit limit and measurement scope per mode.

For `palindrome-length-v1`, Python uses `longest_palindrome(text: str) -> int`;
Java uses `static int longestPalindrome(String text)`; C++ uses
`int longestPalindrome(const string& text)`. Java source is method members
without a class Main or imports; Python source is a function without an input
loop; C++ source is a function without main. The trusted driver supplies the
entry point. C++ driver supplies standard headers and namespace std.

The legacy adapter-level `signature` and `functionName` describe C++ and remain
for existing clients. New clients select metadata from `languageProfiles`.
Only languages supported by both the adapter and the lobby may be offered.
The main backend enforces that intersection; capabilities is not lobby policy.

`inputFormat` is one lowercase a-z line (empty allowed); `outputFormat` is a
nonnegative integer. Each TestCase stores one such input and expected output.
The internal batch count/header is generated by the engine, never by the team
backend or student. Test cases use the same serialization across languages.

FUNCTION: CPU/wall/memory limits apply to the entire suite process; measured
runtime includes the driver, interpreter/JVM and all function invocations.
FULL_PROGRAM: limits apply per test; a measured round sums process CPU time
across cases and uses the maximum process peak memory. Both modes report the
median of five round times and the maximum memory among measured runs,
excluding compilation. Run measures its single process. Rankings must keep
language, mode, task/test version and environment consistent.

### Aktualizacja zakresu FUNCTION

Nowy benchmark funkcji używa `FUNCTION_CALLS_ELAPSED` zamiast pomiaru całego
procesu CPU. `benchmark.measurementTrust` określa zaufanie do metryki,
`benchmark.sandboxCpuTimeMs` zachowuje zewnętrzny czas CPU całego benchmarku.
Szczegóły i ograniczenia: [function-benchmark.md](function-benchmark.md).
Ten opis zastępuje wcześniejsze opisy pomiaru FUNCTION w dokumentacji.
