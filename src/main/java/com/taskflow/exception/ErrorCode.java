package com.taskflow.exception;

public enum ErrorCode {
    TASK_EMPTY("TASK_EMPTY", 400, "任务描述不能为空"),
    TASK_TOO_LONG("TASK_TOO_LONG", 400, "任务描述超长"),
    TOOL_NOT_FOUND("TOOL_NOT_FOUND", 400, "工具未注册或不可用"),
    TOOL_TIMEOUT("TOOL_TIMEOUT", 500, "工具执行超时"),
    UNAUTHORIZED("UNAUTHORIZED", 401, "API Key 无效或缺失"),
    FORBIDDEN("FORBIDDEN", 403, "权限不足"),
    RATE_LIMITED("RATE_LIMITED", 429, "请求频率超限"),
    TASK_NOT_FOUND("TASK_NOT_FOUND", 404, "任务ID不存在"),
    TASK_TIMEOUT("TASK_TIMEOUT", 408, "任务执行超时"),
    STEP_LIMIT_EXCEEDED("STEP_LIMIT_EXCEEDED", 422, "超过最大执行步数"),
    LLM_UNAVAILABLE("LLM_UNAVAILABLE", 502, "LLM 服务不可用"),
    INTERNAL_ERROR("INTERNAL_ERROR", 500, "服务器内部错误");

    private final String code;
    private final int httpStatus;
    private final String message;

    ErrorCode(String code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public String getCode() { return code; }
    public int getHttpStatus() { return httpStatus; }
    public String getMessage() { return message; }
}
