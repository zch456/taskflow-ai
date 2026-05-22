package com.taskflow.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "task_executions", indexes = {
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_created_at", columnList = "createdAt")
})
public class TaskExecutionEntity {

    @Id
    @Column(length = 36)
    private String taskId;

    @Column(length = 36, nullable = false)
    private String traceId;

    @Column(nullable = false, length = 2000)
    private String originalTask;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExecutionStatus status;

    @Column(columnDefinition = "TEXT")
    private String finalResult;

    private int stepCount;
    private int retryCount;
    private int maxSteps;
    private int timeoutSeconds;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private long executionTimeMs;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant startedAt;
    private Instant completedAt;

    @OneToMany(mappedBy = "taskExecution", cascade = CascadeType.ALL,
            fetch = FetchType.LAZY, orphanRemoval = true)
    private List<ExecutionStepEntity> steps = new ArrayList<>();

    public TaskExecutionEntity() {}

    public TaskExecutionEntity(String taskId, String traceId, String originalTask,
                                ExecutionStatus status, int maxSteps, int timeoutSeconds) {
        this.taskId = taskId;
        this.traceId = traceId;
        this.originalTask = originalTask;
        this.status = status;
        this.maxSteps = maxSteps;
        this.timeoutSeconds = timeoutSeconds;
        this.createdAt = Instant.now();
    }

    public void addStep(ExecutionStepEntity step) {
        steps.add(step);
        step.setTaskExecution(this);
        this.stepCount = steps.size();
    }

    // Getters and setters

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getOriginalTask() { return originalTask; }
    public void setOriginalTask(String originalTask) { this.originalTask = originalTask; }

    public ExecutionStatus getStatus() { return status; }
    public void setStatus(ExecutionStatus status) { this.status = status; }

    public String getFinalResult() { return finalResult; }
    public void setFinalResult(String finalResult) { this.finalResult = finalResult; }

    public int getStepCount() { return stepCount; }
    public void setStepCount(int stepCount) { this.stepCount = stepCount; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public int getMaxSteps() { return maxSteps; }
    public void setMaxSteps(int maxSteps) { this.maxSteps = maxSteps; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public List<ExecutionStepEntity> getSteps() { return steps; }
    public void setSteps(List<ExecutionStepEntity> steps) { this.steps = steps; }
}
