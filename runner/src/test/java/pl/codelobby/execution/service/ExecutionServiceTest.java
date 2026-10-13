package pl.codelobby.execution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.codelobby.execution.api.*;
import pl.codelobby.execution.config.PistonProperties;
import pl.codelobby.execution.judge0.*;
import pl.codelobby.execution.piston.PistonClient;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExecutionServiceTest {
    final ObjectMapper mapper = new ObjectMapper();
    Judge0Client judge;
    ExecutionService service;
    final TrustedEvaluationRequest.Limits limits = new TrustedEvaluationRequest.Limits(1000, 3000, 64000000);
    @BeforeEach void setup() {
        judge = mock(Judge0Client.class);
        service = new ExecutionService(mock(PistonClient.class), new PistonProperties("http://127.0.0.1:1", "10.2.0", 10000, 3000, 268435456, 67108864), judge, mapper, "judge0");
    }
    TrustedEvaluationRequest request() {
        return new TrustedEvaluationRequest("unit-1", TrustedEvaluationRequest.Language.CPP, "int main(){}", limits,
            List.of(new TrustedEvaluationRequest.TestCase("public", "3\n", TrustedEvaluationRequest.Visibility.PUBLIC),
                    new TrustedEvaluationRequest.TestCase("secret", "5\n", TrustedEvaluationRequest.Visibility.HIDDEN)),
            TrustedEvaluationRequest.Mode.FULL_PROGRAM, null);
    }
    Judge0Response outer(String stdout, String status) {
        return new Judge0Response(stdout, null, null, null, .01, .02, 1000., null, 0, new Judge0Response.Status(3, status));
    }
    void suite(String first, String second, Judge0SuiteResponse.Benchmark benchmark) throws Exception {
        var runs = List.of(new Judge0SuiteResponse.CaseResult("Accepted", first, "public diagnostic", .01, 1000., null),
                           new Judge0SuiteResponse.CaseResult("Accepted", second, "secret diagnostic", .02, 2000., null));
        var response = new Judge0SuiteResponse("codelobby-suite-v1", runs, benchmark);
        when(judge.executeSuiteFiles(any(), anyList(), any(), any())).thenReturn(
            outer("CODELOBBY_TEST_SUITE_RESULT_V1:" + mapper.writeValueAsString(response), "Accepted"));
    }
    Judge0SuiteResponse.Benchmark benchmark() {
        return new Judge0SuiteResponse.Benchmark("COMPLETED", 2, 5, .000553917, 30720., "FUNCTION_CALLS_ELAPSED", "IN_PROCESS_UNTRUSTED", .291);
    }
    @Test void normalizesLineEndingsAndTrailingWhitespaceButPreservesLeadingWhitespace() throws Exception {
        suite("3\r\n \t", "5\r", benchmark());
        assertThat(service.submit(request()).passedTests()).isEqualTo(2);
        suite(" 3\n", "5\n", null);
        assertThat(service.submit(request()).status()).isEqualTo("WRONG_ANSWER");
    }
    @Test void redactsHiddenExpectedActualAndStderr() throws Exception {
        suite("3\n", "5\n", benchmark());
        var result = service.submit(request());
        assertThat(result.testResults().get(0).expectedOutput()).isEqualTo("3\n");
        var hidden = result.testResults().get(1);
        assertThat(hidden.output()).isNull(); assertThat(hidden.stderr()).isNull();
        assertThat(hidden.expectedOutput()).isNull();
    }
    @Test void convertsMetricsAndPreservesFractionalTimeAndTrust() throws Exception {
        suite("3", "5", benchmark());
        var result = service.submit(request());
        assertThat(result.benchmark().medianRuntimeMs()).isCloseTo(.553917, within(.000001));
        assertThat(result.peakMemoryBytes()).isEqualTo(30720000);
        assertThat(result.benchmark().sandboxCpuTimeMs()).isEqualTo(291.);
        assertThat(result.benchmark().measurementTrust()).isEqualTo("IN_PROCESS_UNTRUSTED");
        verify(judge, times(1)).executeSuiteFiles(any(), anyList(), any(), any());
    }
    @Test void wrongAnswerHasNoBenchmarkOrAggregateMetrics() throws Exception {
        suite("0", "5", null);
        var result = service.submit(request());
        assertThat(result.status()).isEqualTo("WRONG_ANSWER");
        assertThat(result.passedTests()).isEqualTo(1);
        assertThat(result.benchmark()).isNull(); assertThat(result.runtimeMs()).isNull();
    }
    @Test void failedBenchmarkRetainsCorrectnessWithoutRankingMetrics() throws Exception {
        suite("3", "5", new Judge0SuiteResponse.Benchmark("FAILED", 2, 0, null, null, null, null, null));
        var result = service.submit(request());
        assertThat(result.status()).isEqualTo("ACCEPTED");
        assertThat(result.benchmark().status()).isEqualTo("FAILED");
        assertThat(result.runtimeMs()).isNull(); assertThat(result.peakMemoryBytes()).isNull();
    }
    @Test void rejectsIncompleteBenchmark() throws Exception {
        suite("3", "5", new Judge0SuiteResponse.Benchmark("COMPLETED", 2, 4, .01, 1000., null, null, null));
        assertThatThrownBy(() -> service.submit(request())).isInstanceOf(ExecutionEngineUnavailableException.class);
    }
    @Test void rejectsMalformedSuiteProtocol() {
        when(judge.executeSuiteFiles(any(), anyList(), any(), any())).thenReturn(outer("invalid", "Accepted"));
        assertThatThrownBy(() -> service.submit(request())).isInstanceOf(ExecutionEngineUnavailableException.class);
    }
    @ParameterizedTest @CsvSource({"Accepted,ACCEPTED", "Compilation Error,COMPILATION_ERROR", "Time Limit Exceeded,TIME_LIMIT_EXCEEDED", "Memory Limit Exceeded,MEMORY_LIMIT_EXCEEDED", "Runtime Error (SIGSEGV),RUNTIME_ERROR"})
    void mapsRunStatuses(String upstream, String expected) {
        when(judge.executeFiles(any(), anyString(), any(), any())).thenReturn(outer("", upstream));
        var result = service.runTrusted(new TrustedRunRequest("run", TrustedEvaluationRequest.Language.CPP, "code", "", limits, null, null));
        assertThat(result.status()).isEqualTo(expected);
        assertThat(result.executionTimeMs()).isEqualTo(10);
        assertThat(result.memoryBytes()).isEqualTo(1000000);
    }
    @Test void releasesSandboxAfterUpstreamFailure() {
        when(judge.executeFiles(any(), anyString(), any(), any())).thenThrow(new org.springframework.web.client.ResourceAccessException("offline"))
            .thenReturn(outer("", "Accepted"));
        var req = new TrustedRunRequest("run", TrustedEvaluationRequest.Language.CPP, "code", "", limits, null, null);
        assertThatThrownBy(() -> service.runTrusted(req)).isInstanceOf(ExecutionEngineUnavailableException.class);
        assertThat(service.runTrusted(req).status()).isEqualTo("ACCEPTED");
    }
    @Test void outputLimitHasItsOwnVerdict() {
        when(judge.executeFiles(any(), anyString(), any(), any())).thenReturn(
            new Judge0Response("x", "", null, "Output limit exceeded", .01, .01, 1000., null, 1,
                new Judge0Response.Status(11,"Runtime Error (NZEC)")));
        var result=service.runTrusted(new TrustedRunRequest("out",TrustedEvaluationRequest.Language.CPP,"int main(){}","",limits,null,null));
        assertThat(result.status()).isEqualTo("OUTPUT_LIMIT_EXCEEDED");
    }
    @Test void hiddenDiagnosticsDoNotLeakIntoGlobalMessage() throws Exception {
        var response=new Judge0SuiteResponse("codelobby-suite-v1",List.of(
            new Judge0SuiteResponse.CaseResult("Accepted","3","",.01,1000.,null),
            new Judge0SuiteResponse.CaseResult("Runtime Error (NZEC)","secret","secret",.01,1000.,"hidden input: secret")),null);
        when(judge.executeSuiteFiles(any(),anyList(),any(),any())).thenReturn(outer(
            "CODELOBBY_TEST_SUITE_RESULT_V1:"+mapper.writeValueAsString(response),"Accepted"));
        var result=service.submit(request());
        assertThat(mapper.writeValueAsString(result)).doesNotContain("hidden input", "secret");
    }
}
