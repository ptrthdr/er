package pl.codelobby.execution.api;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.codelobby.execution.service.SourceBundle;

/** Reads at most limit+1 bytes before Jackson sees any JSON, including chunked bodies. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HttpBodyLimitFilter extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest r) {
        String path=r.getServletPath();
        if(path==null || path.isEmpty()) path=r.getRequestURI().substring(r.getContextPath().length());
        return !"POST".equals(r.getMethod()) || !path.startsWith("/internal/v1/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws IOException, ServletException {
        if (request.getContentLengthLong()>SourceBundle.MAX_BODY_BYTES) { reject(response); return; }
        byte[] body=request.getInputStream().readNBytes(SourceBundle.MAX_BODY_BYTES+1);
        if(body.length>SourceBundle.MAX_BODY_BYTES) { reject(response); return; }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var in=new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    public int read(){return in.read();}
                    public int read(byte[] b,int off,int len){return in.read(b,off,len);}
                    public boolean isFinished(){return in.available()==0;}
                    public boolean isReady(){return true;}
                    public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Synchronous JSON endpoints only");}
                };
            }
            @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
        },response);
    }
    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(413);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(response.getWriter(),
                new ApiError("PAYLOAD_TOO_LARGE","Body przekracza 2000000 bajtów",false));
    }
}
