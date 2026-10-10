> Dokument zawiera wcześniejszy etap lub pomiary historyczne. Obecny kontrakt:
> [OpenAPI](openapi.json), [kolejka](async-submissions.md),
> [benchmark FUNCTION](function-benchmark.md), [start zespołu](TEAM-START-HERE.md).

# Batched C++ function evaluation

## Implemented contract

Trusted evaluations may set `mode: "FUNCTION", adapterId: "palindrome-length-v1"` and submit
`int longestPalindrome(const string& text)` without `main()`. The mode is
task-specific: it is not a universal C++ signature parser. Omitted mode or
`FULL_PROGRAM` retains the separate-process evaluation path. The legacy
PALINDROME_FUNCTION mode remains an alias. A second adapter, vowel-count-v1,
illustrates adding another task without a new execution mode. The demo Run
and Submit routes now use the palindrome function adapter.

The server adds includes and a main program. Test inputs are supplied through
stdin (count followed by one line per input). Expected answers remain outside
the student's sandbox. Output must contain exactly one nonnegative integer per
case and no extra lines. The Ruby worker checks the complete output against
trusted expected answers before benchmarking; Spring independently compares
each returned record. Student output cannot supply verdicts or timing metadata.
Timing and memory come from Isolate, not from code inside the student process.

One compilation is followed by one correctness process and, only on success,
two warmup processes plus five measured processes. Each process gets a fresh
sandbox restored from the pristine compiled snapshot. A median represents the
whole suite's CPU runtime, including its single startup, harness and input/output.
Peak memory represents the whole process, not an individual function call.
Per-case runtime and memory are null: attributing process metrics to individual
calls would be misleading.

## Semantics required for integration

- CPU, wall and memory limits apply to the entire suite. They are not the old
  per-case limits. A task author must select its limits for this execution mode.
- Function-local variables reset naturally between calls; globals, static
  storage and allocator state persist within a round. The contract requires
  independent answers on consecutive calls. A class-based adapter should create
  a fresh Solution instance per test. Arbitrary global reset is not implemented.
- A process failure marks the batch failed. Partial completed-case reporting
  and precise attribution of a crash to a test are not provided. No benchmark
  follows an unsuccessful correctness round.
- Hidden outputs and stderr remain redacted. Input visibility within the
  sandbox is not a security boundary: all batch inputs are available to that
  process; expected answers and authoritative metrics are outside it.
- Additional signatures/types require explicit, reviewed trusted adapters owned
  by the task/backend integration. Browser requests cannot select modes or
  supply adapters, cases, expected answers or limits.
- Retain single-process-at-a-time scheduling for comparable benchmarks. The
  current worker's reserved IDs and COUNT=1 restriction remain in force.
- The current unofficial Docker image and privileged worker remain experimental.
  This implements a usable evaluation design, not a completed production
  security audit, distributed queue or public deployment.

## Measurements on local Docker, 2026-10-09

Same 50 input strings and algorithms, converted from main programs to functions:

| | Previous isolated cases | Batched functions |
| --- | ---: | ---: |
| Optimal, Submit wall time | 154.44 s | 5.408 s, 5.164 s |
| Optimal, median suite CPU | 1594 ms | 32 ms, 31 ms |
| Optimal, accepted tests | 50/50 | 50/50 |
| Naive, Submit wall time | 175.87 s | 4.128 s, 4.137 s |
| Naive verdict | Accepted, 4021 ms median CPU | TLE, no benchmark |

The optimal Submit is about 29 times faster (~96.5% less waiting). This is a
small local sample, not an SLA. The naive verdict changes because 2 seconds now
covers the entire suite instead of each case separately; it is not a direct
comparison of accepted benchmark values. CPU numbers use the new measurement
definition and must not be mixed into a ranking with historical per-case totals
or compared directly with LeetCode. No reported time is adjusted artificially.

Both modes compile once. The speedup comes primarily from reducing sandbox and
process launches from 400 to 8, not from reducing the five measured rounds.

On a shorter three-case suite (256, 512 and 1024 identical characters), both
algorithms passed: optimal median CPU 30 ms / Submit 5.198 s; naive 396 ms /
Submit 8.205 s. Wrong answers and extra output were rejected as WRONG_ANSWER,
and abort() as RUNTIME_ERROR. Hidden-output redaction held in all checks.
