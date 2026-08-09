package com.haalkhata.core.config;

import com.haalkhata.core.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequestLogFilter extends OncePerRequestFilter {

    private final AppProperties props;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("requestId", requestId);
        long start = System.currentTimeMillis();

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        try {
            chain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long duration = System.currentTimeMillis() - start;
            String method = request.getMethod();
            String path = request.getRequestURI();
            String query = request.getQueryString();
            int status = response.getStatus();
            String user = currentUserEmail(request);
            boolean success = status < 400;

            String body = "";
            if (props.isLogRequestBody()
                    && !path.startsWith("/api/v1/auth")
                    && wrappedRequest.getContentAsByteArray().length > 0) {
                body = new String(wrappedRequest.getContentAsByteArray(), request.getCharacterEncoding() == null
                        ? "UTF-8" : request.getCharacterEncoding());
                if (body.length() > 500) {
                    body = body.substring(0, 500) + "...(truncated)";
                }
            }

            if (success) {
                log.info("[{}] {} {}{} -> {} in {}ms{}",
                        requestId, method, path, query == null ? "" : "?" + query,
                        status, duration,
                        (user == null ? "" : " user=" + user));
            } else {
                log.error("[{}] {} {}{} -> {} in {}ms{}",
                        requestId, method, path, query == null ? "" : "?" + query,
                        status, duration,
                        (user == null ? "" : " user=" + user));
                if (props.isLogRequestBody() && !body.isEmpty()) {
                    log.debug("[{}] request body: {}", requestId, body);
                }
            }

            if (log.isDebugEnabled() && !body.isEmpty() && success) {
                log.debug("[{}] request body: {}", requestId, body);
            }

            wrappedResponse.copyBodyToResponse();
            MDC.remove("requestId");
        }
    }

    private String currentUserEmail(HttpServletRequest request) {
        Object attr = request.getAttribute("currentUser");
        if (attr instanceof CurrentUser cu) {
            return cu.getEmail();
        }
        return null;
    }
}
