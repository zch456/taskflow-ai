package com.taskflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "execution_steps", indexes = {
        @Index(name = "idx_task_id", columnList = "task_execution_task_id")
})
public class ExecutionStepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_execution_task_id", nullable = false)
    private TaskExecutionEntity taskExecution;

    private int stepIndex;

    @Column(length = 1000)
    private String action;

    @Column(nullable = false, length = 100)
    private String toolName;

    @Column(columnDefinition = "TEXT")
    private String inputParams;

    @Column(columnDefinition = "TEXT")
    private String outputResult;

    private long executionTimeMs;
    private boolean success;

    @Column(length = 1000)
    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt;

    public ExecutionStepEntity() {}

    public ExecutionStepEntity(int stepIndex, String action, String toolName,
                                String inputParams) {
        this.stepIndex = stepIndex;
        this.action = action;
        this.toolName = toolName;
        this.inputParams = inputParams;
        this.createdAt = Instant.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public TaskExecutionEntity getTaskExecution() { return taskExecution; }
    public void setTaskExecution(TaskExecutionEntity taskExecution) {
        this.taskExecution = taskExecution;
    }

    public int getStepIndex() { return stepIndex; }
    public void setStepIndex(int stepIndex) { this.stepIndex = stepIndex; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getInputParams() { return inputParams; }
    public void setInputParams(String inputParams) { this.inputParams = inputParams; }

    public String getOutputResult() { return outputResult; }
    public void setOutputResult(String outputResult) { this.outputResult = outputResult; }

    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
