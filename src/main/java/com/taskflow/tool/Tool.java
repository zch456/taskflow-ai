package com.taskflow.tool;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface Tool {

    String getName();

    String getDescription();

    Map<String, ParameterSchema> getInputSchema();

    ToolResult execute(Map<String, Object> params);

    default CompletableFuture<ToolResult> executeAsync(Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> execute(params));
    }

    default long getTimeoutMs() {
        return 30_000;
    }

    default int getMaxRetries() {
        return 0;
    }

    default boolean isIdempotent() {
        return false;
    }
}
