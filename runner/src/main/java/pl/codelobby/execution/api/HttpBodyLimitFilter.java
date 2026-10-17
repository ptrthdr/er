package pl.codelobby.execution.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.codelobby.execution.service.SourceBundle;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Reads at most limit+1 bytes before Jackson sees any JSON, including chunked bodies. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HttpBodyLimitFilter extends OncePerRequestFilter {

    private final ApiErrorWriter errors;

    public HttpBodyLimitFilter(ApiErrorWriter errors) {
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI().substring(request.getContextPath().length());
        }
        return !"POST".equals(request.getMethod()) || !path.startsWith("/internal/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain chain) throws IOException, ServletException {
        if (request.getContentLengthLong() > SourceBundle.MAX_BODY_BYTES) {
            reject(response);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(SourceBundle.MAX_BODY_BYTES + 1);
        if (body.length > SourceBundle.MAX_BODY_BYTES) {
            reject(response);
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public ServletInputStream getInputStream() {
                var input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override
                    public int read() {
                        return input.read();
                    }

                    @Override
                    public int read(byte[] bytes, int offset, int length) {
                        return input.read(bytes, offset, length);
                    }

                    @Override
                    public boolean isFinished() {
                        return input.available() == 0;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setReadListener(ReadListener listener) {
                        throw new UnsupportedOperationException("Synchronous JSON endpoints only");
                    }
                };
            }

            @Override
            public BufferedReader getReader() {
                return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
            }
        }, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        errors.write(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                new ApiError("PAYLOAD_TOO_LARGE",
                        "Body przekracza " + SourceBundle.MAX_BODY_BYTES + " bajtów", false));
    }
}
