package pl.codelobby.execution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import pl.codelobby.execution.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubmissionJobsTest {
    ExecutionService engine;
    SubmissionJobs jobs;
    ValidatorFactory factory;
    @BeforeEach void setup() {
        engine = mock(ExecutionService.class);
        factory = Validation.buildDefaultValidatorFactory();
        jobs = new SubmissionJobs(engine, new ObjectMapper(), factory.getValidator());
    }
    @AfterEach void close() { jobs.stop(); factory.close(); }
    TrustedEvaluationRequest request(String id, String code) {
        return new TrustedEvaluationRequest(id, TrustedEvaluationRequest.Language.CPP, code,
            new TrustedEvaluationRequest.Limits(1000, 3000, 64000000),
            List.of(new TrustedEvaluationRequest.TestCase("", "", TrustedEvaluationRequest.Visibility.PUBLIC)), null, null);
    }
    @Test void rejectsInvalidStructureBeforeCallingEngine() {
        assertThatThrownBy(() -> jobs.enqueue(request("", "code"))).isInstanceOf(InvalidEvaluationException.class);
        verifyNoInteractions(engine);
    }
    @Test void rejectsSemanticFailureBeforeScheduling() {
        doThrow(new InvalidEvaluationException("adapter")).when(engine).validateSubmission(any());
        assertThatThrownBy(() -> jobs.enqueue(request("id", "code"))).isInstanceOf(InvalidEvaluationException.class);
        verify(engine, never()).submitQueued(any(), any());
    }
    @Test void deduplicatesIdenticalRequestAndRejectsDifferentContent() {
        var req = request("same", "code");
        jobs.enqueue(req); jobs.enqueue(req);
        assertThatThrownBy(() -> jobs.enqueue(request("same", "changed")))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    }
    @Test void unknownIdIs404() {
        assertThatThrownBy(() -> jobs.get("missing")).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }
    @Test void stateChangesAfterSandboxCallbackAndFinishes() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), start = new CountDownLatch(1), finish = new CountDownLatch(1);
        when(engine.submitQueued(any(), any())).thenAnswer(call -> {
            entered.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            call.getArgument(1, Runnable.class).run();
            if (!finish.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            return new SubmissionResponse("WRONG_ANSWER", 0, 1, null, null, null, List.of(), null, null, null);
        });
        try {
            jobs.enqueue(request("state", "code")); assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(jobs.get("state").state()).isEqualTo(SubmissionJobs.State.QUEUED);
            start.countDown();
            awaitState("state", SubmissionJobs.State.RUNNING);
            assertThat(jobs.get("state").startedAt()).isNotNull();
            finish.countDown(); awaitState("state", SubmissionJobs.State.FINISHED);
            assertThat(jobs.get("state").result().status()).isEqualTo("WRONG_ANSWER");
        } finally { start.countDown(); finish.countDown(); }
    }
    @Test void infrastructureFailureBecomesFailedWithoutLeakingException() throws Exception {
        when(engine.submitQueued(any(), any())).thenThrow(new IllegalStateException("sensitive detail"));
        jobs.enqueue(request("failure", "code")); awaitState("failure", SubmissionJobs.State.FAILED);
        assertThat(jobs.get("failure").error().message()).doesNotContain("sensitive");
    }
    @Test void queueCapacityRejectsAndDoesNotRetainRejectedId() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(engine.submitQueued(any(), any())).thenAnswer(call -> { entered.countDown(); release.await(5, TimeUnit.SECONDS); return null; });
        try {
            jobs.enqueue(request("active", "code")); assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            for (int i=0; i<10; i++) jobs.enqueue(request("queued-"+i, "code"));
            assertThatThrownBy(() -> jobs.enqueue(request("overflow", "code"))).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(429));
            assertThatThrownBy(() -> jobs.get("overflow")).isInstanceOf(ResponseStatusException.class);
        } finally { release.countDown(); }
    }
    @Test void fileOrderDoesNotChangeFingerprintButTaskVersionDoes() throws Exception {
        var first=new TrustedEvaluationRequest("canonical","v1",TrustedEvaluationRequest.Language.PYTHON,
            TrustedEvaluationRequest.Mode.FULL_PROGRAM,null,List.of(new SourceFile("main.py","pass"),new SourceFile("helper.py","pass")),
            new TrustedEvaluationRequest.Limits(1000,3000,64000000),List.of(new TrustedEvaluationRequest.TestCase("","",TrustedEvaluationRequest.Visibility.PUBLIC)));
        jobs.enqueue(first);awaitState("canonical",SubmissionJobs.State.FINISHED);
        var reordered=new TrustedEvaluationRequest(first.submissionId(),first.taskVersionId(),first.language(),first.mode(),null,
            List.of(first.sourceFiles().get(1),first.sourceFiles().get(0)),first.limits(),first.testCases());
        assertThat(jobs.enqueue(reordered).createdAt()).isEqualTo(jobs.get("canonical").createdAt());
        verify(engine,times(1)).submitQueued(any(),any());
        var changed=new TrustedEvaluationRequest(first.submissionId(),"v2",first.language(),first.mode(),null,
            first.sourceFiles(),first.limits(),first.testCases());
        assertThatThrownBy(()->jobs.enqueue(changed)).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(409));
    }
    void awaitState(String id, SubmissionJobs.State state) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (jobs.get(id).state() != state && System.nanoTime() < deadline) Thread.sleep(5);
        assertThat(jobs.get(id).state()).isEqualTo(state);
    }
}
