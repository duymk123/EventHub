package com.eventhub.auth.exception;

import com.eventhub.auth.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Chuyển lỗi của controller thành ErrorResponse. Chỉ xử lý BusinessException và lỗi dữ liệu đầu vào; các lỗi
 * khác giữ hành vi mặc định của Spring để không đổi cách các API hồ sơ hiện có đang trả lỗi.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException error, HttpServletRequest request) {
        return ResponseEntity.status(error.getErrorCode().httpStatus()).body(new ErrorResponse(
                error.getErrorCode().name(), error.getMessage(), Instant.now(), request.getRequestURI()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleValidation(Exception error, HttpServletRequest request) {
        String message = "Invalid request body";
        if (error instanceof MethodArgumentNotValidException invalid && invalid.getBindingResult().getFieldError() != null) {
            var field = invalid.getBindingResult().getFieldError();
            message = field.getField() + ": " + field.getDefaultMessage();
        }
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "VALIDATION_ERROR", message, Instant.now(), request.getRequestURI()));
    }
}
