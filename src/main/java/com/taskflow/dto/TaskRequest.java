package com.taskflow.dto;

import jakarta.validation.constraints.NotBlank;

public record TaskRequest(
        @NotBlank(message = "任务描述不能为空")
        String task,
        Integer timeout,
        Integer maxSteps
) {
    public int timeoutOrDefault() {
        return timeout != null && timeout > 0 ? timeout : 60;
    }

    public int maxStepsOrDefault() {
        return maxSteps != null && maxSteps > 0 ? maxSteps : 10;
    }
}
