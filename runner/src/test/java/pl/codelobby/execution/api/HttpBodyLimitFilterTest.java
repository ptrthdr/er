package pl.codelobby.execution.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import pl.codelobby.execution.service.SourceBundle;

import static org.assertj.core.api.Assertions.assertThat;

class HttpBodyLimitFilterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpBodyLimitFilter filter = new HttpBodyLimitFilter(new ApiErrorWriter(mapper));

    @Test
    void rejectsOversizedBodyBeforeParser() throws Exception {
        var request = new MockHttpServletRequest("POST", "/internal/v1/runs");
        request.setContent(new byte[SourceBundle.MAX_BODY_BYTES + 1]);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(mapper.readValue(response.getContentAsByteArray(), ApiError.class))
                .isEqualTo(new ApiError("PAYLOAD_TOO_LARGE",
                        "Body przekracza " + SourceBundle.MAX_BODY_BYTES + " bajtów", false));
    }

    @Test
    void alsoBoundsBodiesWithoutContentLength() throws Exception {
        var request = new MockHttpServletRequest("POST", "/internal/v1/evaluation-jobs") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(new byte[SourceBundle.MAX_BODY_BYTES + 1]);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
        assertThat(mapper.readTree(response.getContentAsByteArray()).path("code").asText())
                .isEqualTo("PAYLOAD_TOO_LARGE");
    }

    @Test
    void preservesBodyForJacksonAtBoundary() throws Exception {
        var request = new MockHttpServletRequest("POST", "/internal/v1/runs");
        request.setContent(new byte[SourceBundle.MAX_BODY_BYTES]);
        var chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest().getInputStream().readAllBytes()).hasSize(SourceBundle.MAX_BODY_BYTES);
    }
}
