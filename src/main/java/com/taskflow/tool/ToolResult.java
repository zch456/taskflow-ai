package com.taskflow.tool;

import com.fasterxml.jackson.databind.ObjectMapper;

public record ToolResult(
        boolean success,
        Object data,
        String errorMessage,
        long executionTimeMs
) {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static ToolResult success(Object data, long executionTimeMs) {
        return new ToolResult(true, data, null, executionTimeMs);
    }

    public static ToolResult failure(String errorMessage, long executionTimeMs) {
        return new ToolResult(false, null, errorMessage, executionTimeMs);
    }

    public String dataAsJson() {
        try {
            return mapper.writeValueAsString(data);
        } catch (Exception e) {
            return "{}";
        }
    }
}
