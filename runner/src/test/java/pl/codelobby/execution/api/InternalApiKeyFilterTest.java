package pl.codelobby.execution.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class InternalApiKeyFilterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ApiErrorWriter errors = new ApiErrorWriter(mapper);

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "                                "})
    void disabledKeyReturns503(String key) throws Exception {
        var request = new MockHttpServletRequest("GET", "/internal/v1/capabilities");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        new InternalApiKeyFilter(key, errors).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(mapper.readValue(response.getContentAsByteArray(), ApiError.class))
                .isEqualTo(new ApiError("EXECUTION_UNAVAILABLE", "Wewnętrzne API nie jest skonfigurowane", true));
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void contextPathDoesNotBypassAuthentication() throws Exception {
        var request = new MockHttpServletRequest("GET", "/execution/internal/v1/capabilities");
        request.setContextPath("/execution");
        var response = new MockHttpServletResponse();

        new InternalApiKeyFilter("test-key-at-least-32-characters-long", errors)
                .doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(mapper.readValue(response.getContentAsByteArray(), ApiError.class))
                .isEqualTo(new ApiError("UNAUTHORIZED", "Brak lub nieprawidłowy klucz API", false));
    }

    @Test
    void outsideInternalApiPassesThrough() throws Exception {
        var request = new MockHttpServletRequest("GET", "/other");
        var chain = new MockFilterChain();

        new InternalApiKeyFilter("", errors).doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }
}
