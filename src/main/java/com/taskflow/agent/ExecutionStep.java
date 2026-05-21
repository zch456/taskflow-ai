package com.taskflow.agent;

import com.taskflow.tool.ToolResult;

import java.time.Instant;

public class ExecutionStep {

    private final int stepIndex;
    private final String action;
    private final String toolName;
    private final String inputParams;
    private String outputResult;
    private long executionTimeMs;
    private Instant createdAt;
    private boolean success;
    private String errorMessage;

    public ExecutionStep(int stepIndex, String action, String toolName, String inputParams) {
        this.stepIndex = stepIndex;
        this.action = action;
        this.toolName = toolName;
        this.inputParams = inputParams;
        this.createdAt = Instant.now();
    }

    public void complete(ToolResult result) {
        this.outputResult = result.dataAsJson();
        this.executionTimeMs = result.executionTimeMs();
        this.success = result.success();
        this.errorMessage = result.errorMessage();
    }

    public int getStepIndex() { return stepIndex; }
    public String getAction() { return action; }
    public String getToolName() { return toolName; }
    public String getInputParams() { return inputParams; }
    public String getOutputResult() { return outputResult; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }

    public void setOutputResult(String outputResult) { this.outputResult = outputResult; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
    public void setSuccess(boolean success) { this.success = success; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
