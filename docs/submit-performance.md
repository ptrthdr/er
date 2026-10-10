> Dokument zawiera wcześniejszy etap lub pomiary historyczne. Obecny kontrakt:
> [OpenAPI](openapi.json), [kolejka](async-submissions.md),
> [benchmark FUNCTION](function-benchmark.md), [start zespołu](TEAM-START-HERE.md).

# Submit performance in local Docker

Measured on 2026-10-09 using the existing Apple Silicon Docker Desktop,
the amd64 Judge0 fork, one worker, and the three-case double-number fixture.
This is a local comparison, not a guarantee for other problems or machines.

## Results

| Measurement | Before | After |
| --- | ---: | ---: |
| HTTP Submit duration | 28.55 s | 11.27 s |
| Worker duration | 27.79 s | 10.26 s |
| Sandbox reset duration, total | 23.15 s | 6.18 s |
| Compilation | 0.92 s | 0.81 s |
| Invocation duration, total | 2.74 s | 2.84 s |

Two further accepted evaluations took 11.34 s and 11.26 s. One profiled
baseline and three optimized samples are insufficient to establish statistical
equivalence, but identify preparation as the dominant removable cost.
The HTTP reduction was about 60%, or 2.5 times faster for this fixture.
Timing phases are nested: reset includes cleanup, initialization and restoration;
do not add all phase values together.

## Change

The worker batches file creation, ownership changes, restoration and cleanup
instead of repeatedly launching a shell and sudo for each file. Arguments are
passed separately from shell code. Cleanup also removes dotfiles.

The benchmark still uses one compilation, initial correctness evaluation,
two warmup rounds and five measured rounds of the same test suite. Every
invocation gets a fresh Isolate box and compiled snapshot. Resource limits,
network isolation, CPU timing and cgroup peak-memory measurement are unchanged.
Host preparation diagnostics never enter student runtime.

Verification covered accepted submissions (all five measurements completed),
wrong answer (benchmark skipped), a file-writing case followed by a case that
must not see that file (including all benchmark repetitions), hidden-output
redaction, and a 300 MB allocation rejected as MEMORY_LIMIT_EXCEEDED.

Worker logs contain CODELOBBY_TIMING with monotonic wall-time diagnostics.
They contain phase durations and submission IDs, not test inputs or answers.

## Interpretation and further options

The accepted fixture's median suite CPU values were 88 ms before and
92 / 90 / 90 ms after. Peak memory was approximately 4.2–4.5 MB. These very
short processes include the execution wrapper and startup; small fluctuations
are expected. This comparison does not prove function-level timing accuracy.
Use sufficiently substantial benchmark inputs and keep ranking comparisons
within the same runtime/compiler/machine configuration.

Next options, in order:

1. Provide a separate representative benchmark suite through the trusted
   backend contract. Keep correctness cases exhaustive; repeat only the agreed
   benchmark workload. This changes what runtime represents and needs a contract
   change, so it was not applied here.
2. Return correctness and benchmark progress asynchronously. This improves
   perceived responsiveness but does not shorten actual computation.
3. Check Docker's virtualization and Rosetta configuration for amd64 workloads
   on Apple Silicon. Do not switch blindly; measure compatibility and timings
   before and after. Docker VMM currently does not support Rosetta:
   https://docs.docker.com/desktop/features/vmm/

Do not parallelize measured runs within this worker: overlapping CPU and memory
pressure changes the measurements, and the current sandbox IDs require COUNT=1.
Do not reuse a dirty sandbox to save setup cost. Keep compilation outside
runtime accounting, following Isolate's process/cgroup measurement model:
https://github.com/ioi/isolate/blob/master/isolate.1.txt

Docker Desktop runs Linux containers through its Linux VM; no separate manually
managed Linux server is necessary for this local demonstration.
