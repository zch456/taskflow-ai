package com.taskflow.exception;

public class TaskFlowException extends RuntimeException {

    private final ErrorCode errorCode;

    public TaskFlowException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public TaskFlowException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() { return errorCode; }
}
