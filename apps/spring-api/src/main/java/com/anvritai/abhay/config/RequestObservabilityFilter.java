package com.anvritai.abhay.config;

import jakarta.servlet.*; import jakarta.servlet.http.*; import java.io.IOException; import java.util.UUID;
import org.slf4j.*; import org.springframework.core.Ordered; import org.springframework.core.annotation.Order; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter;

@Component @Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestObservabilityFilter extends OncePerRequestFilter {
 private static final Logger log=LoggerFactory.getLogger(RequestObservabilityFilter.class); private static final int MAX_ID=100;
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  String requestId=safe(request.getHeader("X-Request-ID"));String correlationId=safe(request.getHeader("X-Correlation-ID"));
  if(requestId==null)requestId=UUID.randomUUID().toString();if(correlationId==null)correlationId=requestId;
  long start=System.nanoTime();MDC.put("requestId",requestId);MDC.put("correlationId",correlationId);response.setHeader("X-Request-ID",requestId);response.setHeader("X-Correlation-ID",correlationId);
  try{chain.doFilter(request,response);}finally{long elapsed=(System.nanoTime()-start)/1_000_000;MDC.put("executionTimeMs",Long.toString(elapsed));if(elapsed>=1000)log.warn("slow_request method={} path={} status={} executionTimeMs={}",request.getMethod(),request.getRequestURI(),response.getStatus(),elapsed);else log.info("request_complete method={} path={} status={} executionTimeMs={}",request.getMethod(),request.getRequestURI(),response.getStatus(),elapsed);MDC.clear();}
 }
 private String safe(String value){if(value==null||value.isBlank()||value.length()>MAX_ID||!value.matches("[A-Za-z0-9._:-]+"))return null;return value;}
}
