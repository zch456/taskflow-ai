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
import java.util.concurrent.ConcurrentHashMap;

@Component
public class IdempotencyFilter implements Filter {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final ConcurrentHashMap<String, String> localStore = new ConcurrentHashMap<>();
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

        // Atomic check-and-set to prevent race conditions under concurrency
        if (!acquireIdempotencyKey(idempotencyKey)) {
            httpResponse.setStatus(HttpServletResponse.SC_CONFLICT);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("""
                    {"code": "IDEMPOTENCY_CONFLICT", "message": "幂等键已被使用", "detail": "key: %s"}
                    """.formatted(idempotencyKey));
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean acquireIdempotencyKey(String key) {
        String redisKey = "taskflow:idempotency:" + key;
        if (redisTemplate != null) {
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(redisKey, "1", IDEMPOTENCY_TTL);
            return Boolean.TRUE.equals(success);
        }
        // ConcurrentHashMap.putIfAbsent returns null if key was absent (acquired lock)
        return localStore.putIfAbsent(key, "") == null;
    }
}
