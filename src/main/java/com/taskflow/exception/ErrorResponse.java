package com.taskflow.exception;

import java.time.Instant;

public record ErrorResponse(
        String errorCode,
        String message,
        String detail,
        String traceId,
        Instant timestamp
) {
    public static ErrorResponse of(ErrorCode errorCode, String detail, String traceId) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(),
                detail, traceId, Instant.now());
    }
}
