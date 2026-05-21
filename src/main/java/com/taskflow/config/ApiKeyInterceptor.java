package com.taskflow.config;

import com.taskflow.exception.ErrorCode;
import com.taskflow.exception.TaskFlowException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    private final TaskFlowProperties properties;

    public ApiKeyInterceptor(TaskFlowProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        if (!properties.getAuth().isEnabled()) {
            return true;
        }

        // Skip auth for health checks and metrics
        String path = request.getRequestURI();
        if (path.startsWith("/actuator")) {
            return true;
        }

        String headerName = properties.getAuth().getHeaderName();
        String apiKey = request.getHeader(headerName);

        if (apiKey == null || apiKey.isBlank()) {
            throw new TaskFlowException(ErrorCode.UNAUTHORIZED,
                    "缺少 " + headerName + " 请求头");
        }

        // Validate against configured API keys (comma-separated)
        String validKeys = properties.getAuth().getApiKey();
        if (validKeys == null || validKeys.isBlank()) {
            return true; // No keys configured, allow all
        }

        for (String key : validKeys.split(",")) {
            if (key.trim().equals(apiKey)) {
                return true;
            }
        }

        throw new TaskFlowException(ErrorCode.UNAUTHORIZED, "无效的 API Key");
    }
}
