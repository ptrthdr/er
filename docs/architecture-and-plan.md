> Dokument zawiera wcześniejszy etap lub pomiary historyczne. Obecny kontrakt:
> [OpenAPI](openapi.json), [kolejka](async-submissions.md),
> [benchmark FUNCTION](function-benchmark.md), [start zespołu](TEAM-START-HERE.md).

# CodeLobby execution module: architecture and delivery plan

## Current decision

Judge0 is the active development engine because its worker can compile one C++ submission and run it several times in fresh Isolate boxes. Piston remains in the repository and can still be run on a separate port for comparison. The current Judge0 image is an unofficial 1.13.1-derived fork, patched locally for Apple Silicon Docker Desktop; it is an experiment, not a production recommendation.

Do not let Spring Boot execute student code. Spring Boot validates the request, applies fixed resource limits, calls Judge0, maps the result, compares expected output, and returns a normalized response. For this PoC, one fixed task and its hidden cases live in a server-side in-memory fixture. The main CodeLobby backend will eventually own task persistence and pass trusted task data to this module.

## Current local flow

```text
Browser / CodeMirror (:5174)
  -> Spring Boot Judge0 adapter (:8081)
     -> Judge0 API (:2358)
        -> worker + Isolate sandbox
```

Piston remains a separate comparison flow (`5173 -> 8080 -> 2000`). The Judge0 client fixes language ID 54 (C++), network off, CPU/wall-clock/memory/stack/process limits. The browser cannot choose compiler flags or resource limits.

For team integration, the authenticated `/internal/v1/runs` and `/internal/v1/evaluations` endpoints accept trusted tests and per-task CPU/wall/memory limits. The browser-facing demo controller is disabled by default and enabled only with `CODELOBBY_DEMO_ENABLED=true`. See [the integration contract](integration-contract.md). The execution module does not store tasks or submissions.

## Implemented

1. **Run** — custom stdin, stdout/stderr, compiler diagnostics, runtime failures, resource limits, and normalized API response.
2. **Test cases / Submit** — the browser loads `GET /api/tasks/longest-palindrome` with public examples and submits only `taskId` plus `sourceCode`. Spring Boot resolves 50 trusted `PUBLIC`/`HIDDEN` cases in memory. `POST /api/submissions` creates one Judge0 job that compiles once and restores a clean compiled sandbox for each case. Hidden result filtering, output comparison with CRLF normalization and trailing-whitespace removal, and `ACCEPTED` / `WRONG_ANSWER` / runtime statuses remain in Spring Boot.
3. **Automatic Submit benchmark** — after correctness passes, two warmup rounds and five measured rounds of the complete suite yield median CPU runtime and maximum measured peak memory. The same compiled artifact is restored into fresh Isolate boxes. The student UI exposes only Run and Submit. Local preparation optimizations and measurements are described in `submit-performance.md`.
4. **Piston comparison** — kept operational. Its API compiles for every execute request, while the reported run-stage CPU and memory exclude the compile stage.

Submit uses an internal Judge0 worker protocol. Spring Boot sends the case inputs in one job and marks it with an internal `expected_output` value. The worker removes that marker before evaluating user code, keeps the complete suite outside `/box`, and writes only the current input into a fresh Isolate box restored from the pristine compiled snapshot. The worker returns per-case process results; Spring Boot compares them with expected outputs and decides the final CodeLobby status. The top-level Judge0 `Accepted` status means the suite protocol completed, not that all student tests passed.

## Next steps

1. Validate compile error, wrong answer, runtime error, timeout, and memory-limit behavior through the Judge0 adapter and Submit UI.
2. Review and harden the custom worker suite protocol. Confirm that test input and files from one run never enter another test's sandbox.
3. Trigger benchmarking internally only after all submission tests pass, using a fixed benchmark input from the trusted backend.
4. Review the custom worker patch against its exact upstream image; pin the image digest and build a maintained local image instead of relying on mutable `latest` plus a bind-mounted Ruby file.
5. Run the stack on a supported Linux host and verify sandbox limits and resource measurements there. Apple Silicon emulation results are not suitable for ranking.
6. Add bounded output/input sizes, request rate limits, cancellation/queue behavior, and structured internal errors before connecting this module to a multi-user live lobby.
7. Replace the one-task in-memory fixture with trusted task data and hidden cases from the main backend. Never return hidden input, expected output, or actual output to student clients.

## Measurement rules

- Runtime means sandbox-reported CPU time. Compile time and sandbox setup are excluded. The tested Judge0 API omitted `wall_time`, so the adapter uses its `time` field.
- Peak memory is the largest measured per-run sandbox memory value; warmup samples are discarded.
- Measurements from Piston and Judge0 are not directly comparable because their images, compilers, and cgroup environments differ.
- Output comparison normalizes CRLF/CR to LF and ignores trailing whitespace. Internal and leading whitespace remain significant.
- A successful process exit alone is not an accepted solution; acceptance requires every expected-output comparison to pass.

## Local development

Follow `../infra/README.md` to start Judge0, then run the backend and frontend with the port 8081 / 5174 commands in the root README. Keep the Judge0 API bound to localhost. The worker is privileged; do not expose this PoC to untrusted networks.
