package pl.codelobby.execution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pl.codelobby.execution.api.SubmissionResponse;
import pl.codelobby.execution.api.TrustedEvaluationRequest;

/** In-memory job coordination. Persistent submission storage belongs to the main backend. */
@Service
public class SubmissionJobs {
    public enum State { QUEUED, RUNNING, FINISHED, FAILED }
    public record Snapshot(String submissionId, State state, Instant createdAt,
                           Instant startedAt, Instant finishedAt, SubmissionResponse result, pl.codelobby.execution.api.ApiError error) { }
    private static final long RETENTION_SECONDS = 1800;
    private final LinkedHashMap<String, Job> jobs = new LinkedHashMap<>();
    private final ExecutionService engine;
    private final ObjectMapper mapper;
    private final jakarta.validation.Validator validator;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(10), r -> { Thread t = new Thread(r, "submission-worker"); t.setDaemon(true); return t; });
    private static final class Job {
        final String id, fingerprint;
        final Instant created = Instant.now();
        State state = State.QUEUED;
        Instant started, finished;
        SubmissionResponse result;
        pl.codelobby.execution.api.ApiError error;
        Job(String id, String fingerprint) { this.id = id; this.fingerprint = fingerprint; }
        Snapshot snapshot() { return new Snapshot(id, state, created, started, finished, result, error); }
    }
    public SubmissionJobs(ExecutionService engine, ObjectMapper mapper, jakarta.validation.Validator validator) { this.engine = engine; this.mapper = mapper; this.validator = validator; }

    public synchronized Snapshot enqueue(TrustedEvaluationRequest input) {
        expire();
        if (input == null || !validator.validate(input).isEmpty())
            throw new InvalidEvaluationException("Nieprawidłowa struktura zgłoszenia");
        engine.validateSubmission(input);
        // Own an immutable list before handing the request to another thread.
        var request = new TrustedEvaluationRequest(input.submissionId(), input.taskVersionId(), input.language(),
                input.mode(), input.adapterId(), input.sourceFiles().stream()
                    .sorted(java.util.Comparator.comparing(pl.codelobby.execution.api.SourceFile::path)).toList(),
                input.limits(), java.util.List.copyOf(input.testCases()));
        String fingerprint;
        try {
            byte[] bytes = mapper.writeValueAsBytes(request);
            if (bytes.length > 2_000_000) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Zgłoszenie przekracza 2 MB");
            fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("Cannot fingerprint submission", e); }
        Job previous = jobs.get(request.submissionId());
        if (previous != null) {
            if (!previous.fingerprint.equals(fingerprint))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "submissionId już użyte dla innych danych");
            return previous.snapshot();
        }
        if (jobs.size() >= 100) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Magazyn wyników jest pełny; spróbuj później");
        Job job = new Job(request.submissionId(), fingerprint);
        jobs.put(job.id, job);
        try { executor.execute(() -> evaluate(job, request)); }
        catch (java.util.concurrent.RejectedExecutionException e) {
            jobs.remove(job.id);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Kolejka jest pełna; spróbuj później");
        }
        return job.snapshot();
    }
    public synchronized Snapshot get(String id) {
        expire();
        Job job = jobs.get(id);
        if (job == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono zgłoszenia lub wynik wygasł");
        return job.snapshot();
    }
    private void evaluate(Job job, TrustedEvaluationRequest request) {
        SubmissionResponse result = null;
        pl.codelobby.execution.api.ApiError error = null;
        try { result = engine.submitQueued(request, () -> {
            synchronized (this) { job.state = State.RUNNING; job.started = Instant.now(); }
        }); }
        catch (InvalidEvaluationException e) { error = new pl.codelobby.execution.api.ApiError("INVALID_REQUEST", "Nieprawidłowe dane oceny", false); }
        catch (Exception e) { error = new pl.codelobby.execution.api.ApiError("EXECUTION_UNAVAILABLE", "Nie udało się ukończyć oceny", true); }
        synchronized (this) {
            job.result = result; job.error = error; job.finished = Instant.now();
            job.state = error == null ? State.FINISHED : State.FAILED;
        }
    }
    private void expire() {
        Instant cutoff = Instant.now().minusSeconds(RETENTION_SECONDS);
        jobs.values().removeIf(j -> j.finished != null && j.finished.isBefore(cutoff));
    }
    @PreDestroy
    public void stop() { executor.shutdownNow(); }
}
