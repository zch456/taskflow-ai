package com.taskflow.agent;

import com.taskflow.entity.ExecutionStatus;
import com.taskflow.tool.ToolResult;

public record TaskProgressEvent(
        String taskId,
        EventType type,
        Integer stepIndex,
        String message,
        Object data,
        long timestamp
) {
    public enum EventType {
        PLAN_GENERATED,
        STEP_STARTED,
        STEP_COMPLETED,
        STEP_FAILED,
        TASK_COMPLETED,
        TASK_FAILED,
        TASK_TIMEOUT
    }

    public static TaskProgressEvent planGenerated(String taskId, String planAnalysis) {
        return new TaskProgressEvent(taskId, EventType.PLAN_GENERATED,
                null, planAnalysis, null, System.currentTimeMillis());
    }

    public static TaskProgressEvent stepStarted(String taskId, int stepIndex,
                                                 String toolName, Object params) {
        return new TaskProgressEvent(taskId, EventType.STEP_STARTED,
                stepIndex, "开始执行步骤 " + stepIndex + ": " + toolName, params,
                System.currentTimeMillis());
    }

    public static TaskProgressEvent stepCompleted(String taskId, int stepIndex,
                                                   ToolResult result) {
        return new TaskProgressEvent(taskId, EventType.STEP_COMPLETED,
                stepIndex, "步骤 " + stepIndex + " 完成", result.data(),
                System.currentTimeMillis());
    }

    public static TaskProgressEvent stepFailed(String taskId, int stepIndex,
                                                String error) {
        return new TaskProgressEvent(taskId, EventType.STEP_FAILED,
                stepIndex, error, null, System.currentTimeMillis());
    }

    public static TaskProgressEvent taskCompleted(String taskId, String result) {
        return new TaskProgressEvent(taskId, EventType.TASK_COMPLETED,
                null, result, null, System.currentTimeMillis());
    }

    public static TaskProgressEvent taskFailed(String taskId, String error) {
        return new TaskProgressEvent(taskId, EventType.TASK_FAILED,
                null, error, null, System.currentTimeMillis());
    }

    public static TaskProgressEvent taskTimeout(String taskId) {
        return new TaskProgressEvent(taskId, EventType.TASK_TIMEOUT,
                null, "任务执行超时", null, System.currentTimeMillis());
    }
}
