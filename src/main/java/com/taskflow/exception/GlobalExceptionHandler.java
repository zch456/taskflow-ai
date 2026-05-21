package com.taskflow.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TaskFlowException.class)
    public ResponseEntity<ErrorResponse> handleTaskFlowException(TaskFlowException ex) {
        String traceId = UUID.randomUUID().toString();
        log.warn("TaskFlow error [{}] {}: {}", ex.getErrorCode().getCode(), traceId, ex.getMessage());
        ErrorResponse response = ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), traceId);
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus()).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        String traceId = UUID.randomUUID().toString();
        log.warn("Invalid argument [{}]: {}", traceId, ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(ErrorCode.TASK_EMPTY, ex.getMessage(), traceId));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String traceId = UUID.randomUUID().toString();
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("validation failed");
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(ErrorCode.TASK_EMPTY, detail, traceId));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        String traceId = UUID.randomUUID().toString();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(ErrorCode.TASK_EMPTY, "无法解析请求体", traceId));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        String traceId = UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(ErrorCode.UNAUTHORIZED,
                        "缺少必要的请求头: " + ex.getHeaderName(), traceId));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        String traceId = UUID.randomUUID().toString();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(ErrorCode.TASK_EMPTY,
                        "缺少必要参数: " + ex.getParameterName(), traceId));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String traceId = UUID.randomUUID().toString();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(ErrorCode.TASK_EMPTY,
                        "参数类型错误: " + ex.getName(), traceId));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        String traceId = UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCode.TASK_NOT_FOUND,
                        "接口不存在: " + ex.getResourcePath(), traceId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        String traceId = UUID.randomUUID().toString();
        log.error("Unexpected error [{}]: {}", traceId, ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR,
                        "服务器内部错误", traceId));
    }
}
