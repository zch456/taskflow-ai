package com.taskflow.agent;

import com.taskflow.entity.ExecutionStatus;

import java.util.List;

public record TaskResult(
        String taskId,
        ExecutionStatus status,
        String result,
        List<ExecutionStep> executionSteps,
        long executionTimeMs
) {}
