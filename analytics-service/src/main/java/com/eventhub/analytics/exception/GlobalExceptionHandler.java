package com.eventhub.analytics.exception;

import com.eventhub.analytics.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

/**
 * Điểm tập trung chuyển lỗi controller/service thành ErrorResponse. Lỗi xác thực ở security filter được
 * SecurityConfig xử lý trước khi request tới controller.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * Lấy code/status từ BusinessException và thêm thời gian UTC cùng đường dẫn HTTP gốc vào response.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException error, HttpServletRequest request) {
        return ResponseEntity.status(error.getErrorCode().httpStatus()).body(new ErrorResponse(
                error.getErrorCode().code(), error.getMessage(), Instant.now(), request.getRequestURI()));
    }

    /**
     * Gom lỗi body, query/path và Bean Validation về 400 với nội dung an toàn, giúp frontend xử lý cùng một cấu
     * trúc.
     */
    @ExceptionHandler({
            org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.web.bind.ServletRequestBindingException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.HandlerMethodValidationException.class,
            jakarta.validation.ConstraintViolationException.class
    })
    public ResponseEntity<ErrorResponse> handleValidation(Exception error, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR",
                "Invalid request parameters", Instant.now(), request.getRequestURI()));
    }

    /**
     * Đổi AccessDeniedException phát sinh trong controller/service thành 403. Trường hợp bị chặn sớm ở filter
     * do SecurityConfig xử lý.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleDenied(Exception error, HttpServletRequest request) {
        return ResponseEntity.status(403).body(new ErrorResponse("FORBIDDEN",
                "Access denied", Instant.now(), request.getRequestURI()));
    }

    /**
     * Giữ HTTP status của lỗi chuẩn Spring và chuẩn hóa body, lỗi ứng dụng còn lại trả 500 với thông điệp
     * chung. Log chỉ tên lớp lỗi để hạn chế lộ dữ liệu request.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception error, HttpServletRequest request) {
        if (error instanceof org.springframework.web.ErrorResponse response) {
            int status = response.getStatusCode().value();
            return ResponseEntity.status(status).body(new ErrorResponse("HTTP_" + status,
                    "Request could not be processed", Instant.now(), request.getRequestURI()));
        }
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class)
                .error("Request failed with {}", error.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(new ErrorResponse("INTERNAL_ERROR",
                "Unexpected service error", Instant.now(), request.getRequestURI()));
    }

    /**
     * Đăng ký PropertyEditor để UUID trong path/query tuân thủ dạng 36 ký tự. Lỗi binding được handleValidation
     * trả về 400.
     */
    @InitBinder
    public void bindUuid(org.springframework.web.bind.WebDataBinder binder) {
        binder.registerCustomEditor(java.util.UUID.class, new java.beans.PropertyEditorSupport() {
            /**
             * Kiểm tra định dạng UUID trước khi Spring gán giá trị cho tham số controller.
             */
            @Override
            public void setAsText(String value) {
                if (!value.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) {
                    throw new IllegalArgumentException("UUID must use the canonical 36-character format");
                }
                setValue(java.util.UUID.fromString(value));
            }
        });
    }
}
