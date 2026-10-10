package pl.codelobby.execution.config;

import java.net.http.HttpClient;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfiguration {

    @Bean
    RestClient pistonRestClient(RestClient.Builder builder, PistonProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                // Piston's local Express API currently responds with HTTP 400
                // to JDK's default protocol negotiation; pin the local adapter
                // connection to HTTP/1.1.
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
        requestFactory.setReadTimeout(Duration.ofSeconds(20));

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
