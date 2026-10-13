package pl.codelobby.execution.api;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;
class HttpBodyLimitFilterTest {
    @Test void rejectsOversizedBodyBeforeParser() throws Exception {
        var req=new MockHttpServletRequest("POST","/internal/v1/runs");req.setContent(new byte[2000001]);
        var res=new MockHttpServletResponse();var chain=new MockFilterChain();
        new HttpBodyLimitFilter().doFilter(req,res,chain);
        assertThat(res.getStatus()).isEqualTo(413);assertThat(chain.getRequest()).isNull();
        assertThat(res.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
    }
    @Test void alsoBoundsBodiesWithoutContentLength() throws Exception {
        var req=new MockHttpServletRequest("POST","/internal/v1/evaluation-jobs") {
            @Override public long getContentLengthLong(){return -1;}
        };
        req.setContent(new byte[2000001]);var res=new MockHttpServletResponse();var chain=new MockFilterChain();
        new HttpBodyLimitFilter().doFilter(req,res,chain);assertThat(res.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
    }
    @Test void preservesBodyForJacksonAtBoundary() throws Exception {
        var req=new MockHttpServletRequest("POST","/internal/v1/runs");req.setContent(new byte[2000000]);
        var chain=new MockFilterChain();new HttpBodyLimitFilter().doFilter(req,new MockHttpServletResponse(),chain);
        assertThat(chain.getRequest().getInputStream().readAllBytes()).hasSize(2000000);
    }
}
