package pl.codelobby.execution.api;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;
class InternalApiKeyFilterTest {
 @Test void disabledKeyReturns503() throws Exception{
 var req=new MockHttpServletRequest("GET","/internal/v1/capabilities");var res=new MockHttpServletResponse();
 new InternalApiKeyFilter("").doFilter(req,res,new MockFilterChain());
 assertThat(res.getStatus()).isEqualTo(503);assertThat(res.getContentAsString()).contains("EXECUTION_UNAVAILABLE");}
 @Test void contextPathDoesNotBypassAuthentication() throws Exception{
 var req=new MockHttpServletRequest("GET","/execution/internal/v1/capabilities");req.setContextPath("/execution");
 var res=new MockHttpServletResponse();new InternalApiKeyFilter("test-key-at-least-32-characters-long").doFilter(req,res,new MockFilterChain());
 assertThat(res.getStatus()).isEqualTo(401);}
 @Test void outsideInternalApiPassesThrough() throws Exception{
 var req=new MockHttpServletRequest("GET","/other");var res=new MockHttpServletResponse();var chain=new MockFilterChain();
 new InternalApiKeyFilter("").doFilter(req,res,chain);assertThat(chain.getRequest()).isSameAs(req);}
}
