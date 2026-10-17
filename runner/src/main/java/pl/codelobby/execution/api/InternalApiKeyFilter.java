package pl.codelobby.execution.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private final boolean configured;
    private final byte[] expectedKey;
    private final ApiErrorWriter errors;

    public InternalApiKeyFilter(
            @Value("${codelobby.internal.api-key:}") String apiKey,
            ApiErrorWriter errors
    ) {
        this.configured = apiKey.length() >= 32 && !apiKey.isBlank();
        this.expectedKey = apiKey.getBytes(StandardCharsets.UTF_8);
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // Some servlet mappings (including MockMvc) leave servletPath empty.
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI().substring(request.getContextPath().length());
        }
        return !path.equals("/internal/v1") && !path.startsWith("/internal/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (!configured) {
            reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "EXECUTION_UNAVAILABLE");
            return;
        }
        String provided = request.getHeader("X-CodeLobby-Internal-Key");
        byte[] actual = (provided == null ? "" : provided).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedKey, actual)) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED");
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String code) throws IOException {
        String message = status == HttpServletResponse.SC_UNAUTHORIZED
                ? "Brak lub nieprawidłowy klucz API" : "Wewnętrzne API nie jest skonfigurowane";
        errors.write(response, status, new ApiError(code, message,
                status == HttpServletResponse.SC_SERVICE_UNAVAILABLE));
    }
}
