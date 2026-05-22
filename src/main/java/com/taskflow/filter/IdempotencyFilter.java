package com.taskflow.filter;

import com.taskflow.config.TaskFlowProperties;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class IdempotencyFilter implements Filter {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final Map<String, String> localStore =
            Collections.synchronizedMap(new LinkedHashMap<>() {
                private static final int MAX_ENTRIES = 10_000;
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_ENTRIES;
                }
            });
    private final TaskFlowProperties properties;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

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

        if (!"POST".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String idempotencyKey = httpRequest.getHeader("X-Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        // Check if this key has been processed
        if (isDuplicate(idempotencyKey)) {
            httpResponse.setStatus(HttpServletResponse.SC_CONFLICT);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("""
                    {"code": "IDEMPOTENCY_CONFLICT", "message": "幂等键已被使用", "detail": "key: %s"}
                    """.formatted(idempotencyKey));
            return;
        }

        chain.doFilter(request, response);

        // Store key on successful response
        if (httpResponse.getStatus() >= 200 && httpResponse.getStatus() < 300) {
            store(idempotencyKey);
        }
    }

    private boolean isDuplicate(String key) {
        if (redisTemplate != null) {
            return Boolean.TRUE.equals(redisTemplate.hasKey("taskflow:idempotency:" + key));
        }
        return localStore.containsKey(key);
    }

    private void store(String key) {
        if (redisTemplate != null) {
            redisTemplate.opsForValue()
                    .set("taskflow:idempotency:" + key, "1", IDEMPOTENCY_TTL);
        } else {
            localStore.put(key, "");
        }
    }
}
