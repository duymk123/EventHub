package com.eventhub.auth.exception;

import com.eventhub.auth.common.ErrorCode;
import com.eventhub.auth.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;

/**
 * Chuyển lỗi của controller thành ErrorResponse(code, message, timestamp, path). Lỗi không lường trước trả
 * INTERNAL_ERROR, chi tiết chỉ ghi log.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException error, HttpServletRequest request) {
        return respond(error.getErrorCode().httpStatus(), error.getErrorCode().name(), error.getMessage(), request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleValidation(Exception error, HttpServletRequest request) {
        String message = "Invalid request";
        if (error instanceof MethodArgumentNotValidException invalid && invalid.getBindingResult().getFieldError() != null) {
            var field = invalid.getBindingResult().getFieldError();
            message = field.getField() + ": " + field.getDefaultMessage();
        } else if (error instanceof MethodArgumentTypeMismatchException mismatch) {
            message = mismatch.getName() + ": invalid value";
        }
        return respond(400, ErrorCode.VALIDATION_ERROR.name(), message, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException error, HttpServletRequest request) {
        return respond(404, "NOT_FOUND", "Resource not found", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException error,
                                                      HttpServletRequest request) {
        return respond(405, "METHOD_NOT_ALLOWED", "Method not allowed", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception error, HttpServletRequest request) {
        log.error("Unexpected error on {}", request.getRequestURI(), error);
        return respond(500, ErrorCode.INTERNAL_ERROR.name(), "Unexpected error", request);
    }

    private static ResponseEntity<ErrorResponse> respond(int status, String code, String message,
                                                         HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code, message, Instant.now(), request.getRequestURI()));
    }
}
