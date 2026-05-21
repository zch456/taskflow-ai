package com.taskflow.agent;

import com.taskflow.entity.ExecutionStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ExecutionContext {

    private final String taskId;
    private final String traceId;
    private final String originalTask;
    private final List<ExecutionStep> executionHistory;
    private final Map<String, Object> variables;
    private int stepCount;
    private int retryCount;
    private final int maxSteps;
    private final Instant startTime;
    private final Instant expiresAt;
    private ExecutionStatus status;
    private String finalResult;

    public ExecutionContext(String originalTask, int maxSteps, int timeoutSeconds) {
        this.taskId = "task_" + UUID.randomUUID().toString().substring(0, 8);
        this.traceId = UUID.randomUUID().toString();
        this.originalTask = originalTask;
        this.maxSteps = maxSteps;
        this.executionHistory = new ArrayList<>();
        this.variables = new HashMap<>();
        this.stepCount = 0;
        this.retryCount = 0;
        this.startTime = Instant.now();
        this.expiresAt = Instant.now().plusSeconds(timeoutSeconds);
        this.status = ExecutionStatus.PENDING;
    }

    public void markRunning() {
        this.status = ExecutionStatus.RUNNING;
    }

    public void markCompleted(String result) {
        this.status = ExecutionStatus.COMPLETED;
        this.finalResult = result;
    }

    public void markCompleted() {
        this.status = ExecutionStatus.COMPLETED;
    }

    public void markFailed(String error) {
        this.status = ExecutionStatus.FAILED;
        this.finalResult = error;
    }

    public void markTimeout() {
        this.status = ExecutionStatus.TIMEOUT;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean hasReachedMaxSteps() {
        return stepCount >= maxSteps;
    }

    public void addStep(ExecutionStep step) {
        this.executionHistory.add(step);
        this.stepCount++;
    }

    public void incrementRetry() {
        this.retryCount++;
    }

    // Getters
    public String getTaskId() { return taskId; }
    public String getTraceId() { return traceId; }
    public String getOriginalTask() { return originalTask; }
    public List<ExecutionStep> getExecutionHistory() { return executionHistory; }
    public Map<String, Object> getVariables() { return variables; }
    public int getStepCount() { return stepCount; }
    public int getRetryCount() { return retryCount; }
    public int getMaxSteps() { return maxSteps; }
    public Instant getStartTime() { return startTime; }
    public Instant getExpiresAt() { return expiresAt; }
    public ExecutionStatus getStatus() { return status; }
    public String getFinalResult() { return finalResult; }
    public long getElapsedMs() {
        return java.time.Duration.between(startTime, Instant.now()).toMillis();
    }
}
