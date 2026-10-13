package pl.codelobby.execution.piston;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pl.codelobby.execution.config.PistonProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PistonClient {

    private final RestClient restClient;
    private final PistonProperties properties;

    public PistonClient(RestClient pistonRestClient, PistonProperties properties) {
        this.restClient = pistonRestClient;
        this.properties = properties;
    }

    public PistonResponse execute(String sourceCode, String stdin) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("language", "c++");
        request.put("version", properties.cppVersion());
        request.put("files", List.of(Map.of("name", "main.cpp", "content", sourceCode)));
        request.put("stdin", stdin == null ? "" : stdin);
        request.put("compile_timeout", properties.compileTimeoutMs());
        request.put("run_timeout", properties.runTimeoutMs());
        request.put("compile_cpu_time", properties.compileTimeoutMs());
        request.put("run_cpu_time", properties.runTimeoutMs());
        request.put("compile_memory_limit", properties.compileMemoryLimitBytes());
        request.put("run_memory_limit", properties.runMemoryLimitBytes());

        PistonResponse response = restClient.post()
                .uri("/api/v2/execute")
                .body(request)
                .retrieve()
                .body(PistonResponse.class);

        if (response == null || response.run() == null) {
            throw new PistonUnavailableException("Piston zwrócił pustą odpowiedź");
        }
        return response;
    }
}
