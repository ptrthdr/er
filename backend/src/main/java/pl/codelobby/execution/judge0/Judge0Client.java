package pl.codelobby.execution.judge0;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.ResourceAccessException;

import pl.codelobby.execution.config.Judge0Properties;
import pl.codelobby.execution.api.TrustedEvaluationRequest;
import pl.codelobby.execution.service.InvalidEvaluationException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class Judge0Client {

    private static final String SUITE_REQUEST_PREFIX = "CODELOBBY_TEST_SUITE_V1:";
    private static final String SUITE_EXPECTED_OUTPUT = "CODELOBBY_INTERNAL_SUITE_V1";
    private final RestClient restClient;
    private final Judge0Properties properties;
    private final ObjectMapper objectMapper;

    public Judge0Client(RestClient.Builder builder, Judge0Properties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .version(HttpClient.Version.HTTP_1_1)
                .proxy(new ProxySelector() {
                    @Override
                    public List<Proxy> select(URI uri) {
                        return List.of(Proxy.NO_PROXY);
                    }

                    @Override
                    public void connectFailed(URI uri, SocketAddress address, IOException exception) {
                        // The caller receives the connection failure from HttpClient.
                    }
                })
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
    }

    public Judge0Response execute(String sourceCode, String stdin) {
        return execute(sourceCode, stdin, 1);
    }

    public Judge0Response execute(String sourceCode, String stdin, TrustedEvaluationRequest.Limits limits) {
        return execute(sourceCode, stdin, limits, TrustedEvaluationRequest.Language.CPP);
    }

    public Judge0Response execute(String sourceCode, String stdin, TrustedEvaluationRequest.Limits limits,
                                 TrustedEvaluationRequest.Language language) {
        return submit(sourceCode, stdin, 1, false, limits == null ? defaultLimits() : limits, language);
    }

    public Judge0Response executeSuite(String sourceCode, List<TrustedEvaluationRequest.TestCase> cases,
                                       TrustedEvaluationRequest.Limits limits, boolean batch, TrustedEvaluationRequest.Language language) {
        try {
            String stdin = SUITE_REQUEST_PREFIX + objectMapper.writeValueAsString(Map.of(
                    "batch", batch,
                    "inputs", cases.stream().map(TrustedEvaluationRequest.TestCase::input).toList(),
                    "expectedOutputs", cases.stream().map(TrustedEvaluationRequest.TestCase::expectedOutput).toList()));
            return submit(sourceCode, stdin, 1, true, limits, language);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Nie udało się przygotować zestawu testów", exception);
        }
    }

    public Judge0Response execute(String sourceCode, String stdin, int numberOfRuns) {
        return submit(sourceCode, stdin, numberOfRuns, false, defaultLimits(), TrustedEvaluationRequest.Language.CPP);
    }

    public Judge0Response execute(String sourceCode, String stdin, int numberOfRuns,
                                 TrustedEvaluationRequest.Language language) {
        return submit(sourceCode, stdin, numberOfRuns, false, defaultLimits(), language);
    }

    private TrustedEvaluationRequest.Limits defaultLimits() {
        return new TrustedEvaluationRequest.Limits(
                Math.round(properties.cpuTimeLimitSeconds() * 1000),
                Math.round(properties.wallTimeLimitSeconds() * 1000),
                properties.memoryLimitKilobytes() * 1000L);
    }

    public void validateLimits(TrustedEvaluationRequest.Limits limits) {
        if (limits == null || limits.cpuTimeMs() < 1
                || limits.cpuTimeMs() > Math.round(properties.cpuTimeLimitSeconds() * 1000)
                || limits.wallTimeMs() < limits.cpuTimeMs()
                || limits.wallTimeMs() > Math.round(properties.wallTimeLimitSeconds() * 1000)
                || limits.memoryLimitBytes() < 1_000
                || limits.memoryLimitBytes() > properties.memoryLimitKilobytes() * 1000L) {
            throw new InvalidEvaluationException("Limity muszą być dodatnie i nie mogą przekraczać limitów Judge0");
        }
    }

    private Judge0Response submit(String sourceCode, String stdin, int numberOfRuns, boolean suite,
                                  TrustedEvaluationRequest.Limits limits, TrustedEvaluationRequest.Language language) {
        validateLimits(limits);
        Map<String, Object> submission = new LinkedHashMap<>();
        submission.put("source_code", sourceCode);
        submission.put("language_id", switch (language) { case CPP -> properties.languageId(); case PYTHON -> 71; case JAVA -> 62; });
        submission.put("stdin", stdin == null ? "" : stdin);
        submission.put("cpu_time_limit", limits.cpuTimeMs() / 1000.0);
        submission.put("wall_time_limit", limits.wallTimeMs() / 1000.0);
        submission.put("memory_limit", limits.memoryLimitBytes() / 1000);
        submission.put("stack_limit", properties.stackLimitKilobytes());
        submission.put("max_processes_and_or_threads", properties.maxProcessesAndThreads());
        submission.put("enable_network", false);
        submission.put("number_of_runs", numberOfRuns);
        if (suite) submission.put("expected_output", SUITE_EXPECTED_OUTPUT);

        Judge0Response queued = restClient.post()
                .uri("/submissions?base64_encoded=false")
                .body(submission)
                .retrieve()
                .body(Judge0Response.class);
        if (queued == null || queued.token() == null || queued.token().isBlank()) {
            throw new IllegalStateException("Judge0 nie zwrócił tokenu zgłoszenia");
        }

        // Worst case: correctness plus seven benchmark rounds, each case
        // reaching its wall limit; allow preparation overhead and compilation.
        long waitSeconds = suite ? 120 + 50 * 8 * (limits.wallTimeMs() + 1000) / 1000 : 120;
        long deadline = System.nanoTime() + Duration.ofSeconds(waitSeconds).toNanos();
        while (System.nanoTime() < deadline) {
            Judge0Response result = restClient.get()
                    .uri("/submissions/{token}?base64_encoded=true", queued.token())
                    .retrieve()
                    .body(Judge0Response.class);
            if (result != null && result.status() != null && result.status().id() != null
                    && result.status().id() >= 3) {
                try {
                    return result.decoded();
                } catch (IllegalArgumentException exception) {
                    throw new ResourceAccessException("Judge0 zwrócił nieprawidłowe pola Base64", new IOException(exception));
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ResourceAccessException("Przerwano oczekiwanie na wynik Judge0");
            }
        }
        throw new ResourceAccessException("Judge0 przekroczył czas oczekiwania na wynik zgłoszenia");
    }
}
