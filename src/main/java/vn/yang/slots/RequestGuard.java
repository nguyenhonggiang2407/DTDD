package vn.yang.slots;
import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.stream.Collectors;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.yang.slots.Contracts.*;
import static vn.yang.slots.Problem.require;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestGuard extends OncePerRequestFilter {
  static final String COOKIE="yangslots_workspace",WORKSPACE="yangslots.owner";
  private static final int MAX_BODY=256*1024;
  private final Sessions sessions;private final Set<String> hosts;private final String publicOrigin;private final boolean secure;
  RequestGuard(Sessions sessions,Environment env){this.sessions=sessions;hosts=Arrays.stream(env.getProperty("yangslots.allowed-hosts","localhost,127.0.0.1").split(",")).map(String::trim).map(String::toLowerCase).collect(Collectors.toSet());publicOrigin=env.getProperty("yangslots.public-origin","");secure=Boolean.parseBoolean(env.getProperty("yangslots.secure-cookie","false"));}
  @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
    res.setHeader("Cache-Control","no-store");res.setHeader("X-Content-Type-Options","nosniff");res.setHeader("X-Frame-Options","DENY");res.setHeader("Referrer-Policy","same-origin");
    res.setHeader("Content-Security-Policy","default-src 'self';script-src 'self';style-src 'self';img-src 'self' data:;object-src 'none';base-uri 'none';frame-ancestors 'none';form-action 'self'");
    try{
      String rawHost=req.getHeader("Host"),host;
      try{host=URI.create("http://"+rawHost).getHost();}catch(Exception e){throw new Problem(400,"Host không hợp lệ.");}
      require(host!=null&&hosts.contains(host.toLowerCase(Locale.ROOT)),400,"Host không được phép.");
      String path=req.getRequestURI(),method=req.getMethod();
      boolean staticPath=Set.of("/","/assets/app.js","/assets/styles.css","/assets/favicon.svg").contains(path);
      if(staticPath||path.equals("/health")){require(method.equals("GET")||method.equals("HEAD"),405,"Phương thức không được hỗ trợ.");chain.doFilter(req,res);return;}
      require(path.startsWith("/api/"),404,"Không tìm thấy trang.");res.setHeader("Vary","Cookie");
      String token=null;if(req.getCookies()!=null)for(Cookie c:req.getCookies())if(c.getName().equals(COOKIE))token=c.getValue();
      Workspace workspace=sessions.find(token);
      if(path.equals("/api/workspace")&&method.equals("GET")){
        require(!"cross-site".equals(req.getHeader("Sec-Fetch-Site")),403,"Không mở workspace từ website khác.");
        if(workspace==null){Session fresh=sessions.create();workspace=fresh.workspace();res.setHeader("Set-Cookie",COOKIE+"="+fresh.cookie()+"; Path=/; Max-Age=604800; HttpOnly; SameSite=Strict"+(secure?"; Secure":""));}
      }
      require(workspace!=null,401,"Workspace hết hạn. Hãy tải lại trang.");req.setAttribute(WORKSPACE,workspace);
      if(!method.equals("GET")&&!method.equals("HEAD")){
        require(Rules.equal(req.getHeader("X-CSRF-Token"),workspace.csrf()),403,"Token CSRF không hợp lệ.");
        String expected=publicOrigin.isBlank()?req.getScheme()+"://"+rawHost:publicOrigin;
        require(req.getHeader("Origin")==null||req.getHeader("Origin").equals(expected),403,"Origin không được phép.");
        require(req.getHeader("Content-Encoding")==null,415,"Không nhận body đã nén.");
        String type=req.getContentType();require(type!=null&&type.split(";")[0].trim().equalsIgnoreCase("application/json"),415,"Cần application/json.");
        require(req.getContentLengthLong()<=MAX_BODY,413,"Request tối đa 256 KiB.");byte[] body=req.getInputStream().readNBytes(MAX_BODY+1);require(body.length<=MAX_BODY,413,"Request tối đa 256 KiB.");
        try{StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body));}catch(CharacterCodingException e){throw new Problem(400,"JSON cần UTF-8 hợp lệ.");}
        chain.doFilter(new BodyRequest(req,body),res);return;
      }
      chain.doFilter(req,res);
    }catch(Problem problem){if(!res.isCommitted()){res.setStatus(problem.status());res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"error\":\""+escape(problem.getMessage())+"\"}");}}
  }
  private static String escape(String value){StringBuilder out=new StringBuilder();for(char c:value.toCharArray()){if(c=='"'||c=='\\')out.append('\\').append(c);else if(c<32)out.append(String.format("\\u%04x",(int)c));else out.append(c);}return out.toString();}
  private static final class BodyRequest extends HttpServletRequestWrapper{
    private final byte[] body;BodyRequest(HttpServletRequest req,byte[] body){super(req);this.body=body;}
    @Override public int getContentLength(){return body.length;}
    @Override public long getContentLengthLong(){return body.length;}
    @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
    @Override public ServletInputStream getInputStream(){ByteArrayInputStream source=new ByteArrayInputStream(body);return new ServletInputStream(){
      @Override public int read(){return source.read();}
      @Override public int read(byte[] bytes,int offset,int length){return source.read(bytes,offset,length);}
      @Override public boolean isFinished(){return source.available()==0;}
      @Override public boolean isReady(){return true;}
      @Override public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Synchronous input only");}
    };}
  }
}
