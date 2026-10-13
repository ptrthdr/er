package pl.codelobby.execution.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private final String apiKey;

    public InternalApiKeyFilter(@Value("${codelobby.internal.api-key:}") String apiKey) {
        this.apiKey = apiKey;
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
        response.setHeader("Cache-Control", "no-store");
        if (apiKey.length() < 32) {
            reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "EXECUTION_UNAVAILABLE");
            return;
        }
        String provided = request.getHeader("X-CodeLobby-Internal-Key");
        byte[] actual = (provided == null ? "" : provided).getBytes(StandardCharsets.UTF_8);
        byte[] expected = apiKey.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED");
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int code, String error) throws IOException {
        response.setStatus(code);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(response.getWriter(),
                new ApiError(error, code == 401 ? "Brak lub nieprawidłowy klucz API" : "Wewnętrzne API nie jest skonfigurowane", code == 503));
    }
}
