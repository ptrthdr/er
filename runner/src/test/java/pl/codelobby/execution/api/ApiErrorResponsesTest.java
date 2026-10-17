package pl.codelobby.execution.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;
import pl.codelobby.execution.config.Judge0Properties;
import pl.codelobby.execution.judge0.Judge0Client;
import pl.codelobby.execution.service.ExecutionEngineUnavailableException;
import pl.codelobby.execution.service.ExecutionService;
import pl.codelobby.execution.service.SubmissionJobs;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = InternalExecutionController.class,
        properties = "codelobby.internal.api-key=api-error-test-key-at-least-32-characters")
@Import(ApiErrorWriter.class)
class ApiErrorResponsesTest {

    private static final String KEY = "api-error-test-key-at-least-32-characters";
    private static final String PRIVATE_DETAIL = "private-source-and-upstream-diagnostic";
    private static final String EVALUATION = """
            {"submissionId":"job-1","taskVersionId":"version-1","language":"CPP",
             "mode":"FULL_PROGRAM","sourceFiles":[{"path":"main.cpp","content":"private-source"}],
             "limits":{"cpuTimeMs":1000,"wallTimeMs":3000,"memoryLimitBytes":64000000},
             "testCases":[{"input":"","expectedOutput":"","visibility":"PUBLIC"}]}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ExecutionService executionService;

    @MockitoBean
    private SubmissionJobs jobs;

    @MockitoBean
    private Judge0Client judge;

    @BeforeEach
    void configureCapabilities() {
        when(judge.configuration()).thenReturn(new Judge0Properties("http://test", 54, 2, 5,
                128000, 64000, 20));
        when(judge.runtimeVersions()).thenReturn(Map.of("CPP", "test-cpp"));
    }

    static Stream<Arguments> invalidHttpRequests() {
        return Stream.of(
                Arguments.of(put("/internal/v1/runs"), 405, "METHOD_NOT_ALLOWED"),
                Arguments.of(post("/internal/v1/runs").contentType(MediaType.TEXT_PLAIN)
                        .content(PRIVATE_DETAIL), 415, "UNSUPPORTED_MEDIA_TYPE"),
                Arguments.of(get("/internal/v1/capabilities").accept(MediaType.APPLICATION_XML),
                        406, "NOT_ACCEPTABLE"),
                Arguments.of(get("/internal/v1/unknown"), 404, "RESOURCE_NOT_FOUND"),
                Arguments.of(get("/internal/v1/evaluation-jobs/{id}", "bad$id"), 400, "INVALID_REQUEST"),
                Arguments.of(get("/internal/v1/evaluation-jobs/{id}", "a".repeat(129)), 400, "INVALID_REQUEST")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidHttpRequests")
    void frameworkErrorsUseTheApiEnvelope(MockHttpServletRequestBuilder request, int expectedStatus, String code)
            throws Exception {
        var result = mvc.perform(request.header("X-CodeLobby-Internal-Key", KEY))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(PRIVATE_DETAIL);
        if (expectedStatus == 405) {
            assertThat(result.getResponse().getHeader("Allow")).contains("POST");
        }
    }

    @Test
    void validationNamesTheFieldWithoutReturningSourceCode() throws Exception {
        mvc.perform(post("/internal/v1/evaluation-jobs").header("X-CodeLobby-Internal-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVALUATION.replace("\"cpuTimeMs\":1000", "\"cpuTimeMs\":0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("limits.cpuTimeMs")))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(content().string(not(containsString("private-source"))));
        verifyNoInteractions(executionService, jobs);
    }

    @ParameterizedTest
    @CsvSource({"409,SUBMISSION_ID_CONFLICT,false", "429,QUEUE_CAPACITY_EXCEEDED,true",
            "503,EXECUTION_UNAVAILABLE,true", "500,INTERNAL_SERVER_ERROR,false"})
    void statusErrorsDoNotForwardInternalReasons(int expectedStatus, String code, boolean retryable) throws Exception {
        when(jobs.enqueue(any())).thenThrow(new ResponseStatusException(HttpStatus.valueOf(expectedStatus), PRIVATE_DETAIL));

        mvc.perform(post("/internal/v1/evaluation-jobs").header("X-CodeLobby-Internal-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(EVALUATION))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.retryable").value(retryable))
                .andExpect(content().string(not(containsString(PRIVATE_DETAIL))));
    }

    @Test
    void unexpectedFailureReturnsSafeNonRetryable500() throws Exception {
        when(jobs.enqueue(any())).thenThrow(new IllegalStateException(PRIVATE_DETAIL));

        mvc.perform(post("/internal/v1/evaluation-jobs").header("X-CodeLobby-Internal-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(EVALUATION))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(not(containsString(PRIVATE_DETAIL))));
    }

    @Test
    void engineFailureReturnsSafeRetryable503() throws Exception {
        when(executionService.submit(any())).thenThrow(new ExecutionEngineUnavailableException(PRIVATE_DETAIL, null));

        mvc.perform(post("/internal/v1/evaluations").header("X-CodeLobby-Internal-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(EVALUATION))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EXECUTION_UNAVAILABLE"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(content().string(not(containsString(PRIVATE_DETAIL))));
    }

    @Test
    void newJobHasALocationForPolling() throws Exception {
        when(jobs.enqueue(any())).thenReturn(new SubmissionJobs.Snapshot("job-1", SubmissionJobs.State.QUEUED,
                Instant.EPOCH, null, null, null, null));

        mvc.perform(post("/internal/v1/evaluation-jobs").header("X-CodeLobby-Internal-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(EVALUATION))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "http://localhost/internal/v1/evaluation-jobs/job-1"))
                .andExpect(jsonPath("$.submissionId").value("job-1"));
    }
}
