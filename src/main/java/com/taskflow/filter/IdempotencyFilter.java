package com.taskflow.filter;

import com.taskflow.config.TaskFlowProperties;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class IdempotencyFilter implements Filter {

    private final Map<String, String> idempotencyStore =
            Collections.synchronizedMap(new java.util.LinkedHashMap<>() {
                private static final int MAX_ENTRIES = 10_000;
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_ENTRIES;
                }
            });
    private final TaskFlowProperties properties;

    public IdempotencyFilter(TaskFlowProperties properties) {
        this.properties = properties;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (!properties.getAgent().isIdempotencyEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        String method = httpRequest.getMethod();
        if (!"POST".equalsIgnoreCase(method)) {
            chain.doFilter(request, response);
            return;
        }

        String idempotencyKey = httpRequest.getHeader("X-Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        // Check if this key has been processed
        String existingResult = idempotencyStore.get(idempotencyKey);
        if (existingResult != null) {
            httpResponse.setStatus(HttpServletResponse.SC_CONFLICT);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("""
                    {"code": "RATE_LIMITED", "message": "幂等键已被使用", "detail": "key: %s"}
                    """.formatted(idempotencyKey));
            return;
        }

        chain.doFilter(request, response);

        // Store key on successful response
        if (httpResponse.getStatus() >= 200 && httpResponse.getStatus() < 300) {
            idempotencyStore.put(idempotencyKey, "");
        }
    }
}
