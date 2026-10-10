package pl.codelobby.demo;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Only this server-side client holds the engine's key. */
@Component
public class EngineClient {
    private final RestClient http;
    public EngineClient(@Value("${execution.base-url}") String base,
                        @Value("${execution.api-key}") String key) {
        if (key.length() < 32) throw new IllegalArgumentException("Configure CODELOBBY_INTERNAL_API_KEY (at least 32 characters)");
        URI uri = URI.create(base);
        if (!"127.0.0.1".equals(uri.getHost()) && !"localhost".equals(uri.getHost()))
            throw new IllegalArgumentException("This example accepts only a localhost execution service");
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(120));
        http = RestClient.builder().baseUrl(base).requestFactory(factory)
                .defaultHeader("X-CodeLobby-Internal-Key", key).build();
    }
    public ResponseEntity<JsonNode> post(String path, Object body) {
        return http.post().uri(path).body(body).retrieve().toEntity(JsonNode.class);
    }
    public JsonNode getJob(String id) {
        return http.get().uri("/internal/v1/evaluation-jobs/{id}", id).retrieve().body(JsonNode.class);
    }
}
