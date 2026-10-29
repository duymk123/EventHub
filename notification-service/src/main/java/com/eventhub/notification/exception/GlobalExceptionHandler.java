package com.eventhub.notification.exception;

import com.eventhub.notification.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Bộ xử lý lỗi toàn cục — ánh xạ BusinessException thành định dạng ErrorResponse thống nhất.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                ex.getErrorCode().code(),
                ex.getMessage(),
                java.time.Instant.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(ex.getErrorCode().httpStatus()).body(body);
    }
}
