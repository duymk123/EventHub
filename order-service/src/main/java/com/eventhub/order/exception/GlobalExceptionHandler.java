package com.eventhub.order.exception;

import com.eventhub.order.common.ErrorCode;
import com.eventhub.order.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;

/**
 * Chuyển mọi lỗi của controller thành ErrorResponse(code, message, timestamp, path). Lỗi không lường trước trả
 * INTERNAL_ERROR và chỉ ghi chi tiết vào log, không lộ exception ra response.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException error, HttpServletRequest request) {
        return ResponseEntity.status(error.getErrorCode().httpStatus())
                .body(new ErrorResponse(error.getErrorCode().name(), error.getMessage(), Instant.now(),
                        request.getRequestURI(), error.getTakenSeatIds()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            HandlerMethodValidationException.class, MissingRequestHeaderException.class})
    public ResponseEntity<ErrorResponse> handleValidation(Exception error, HttpServletRequest request) {
        String message = "Invalid request";
        if (error instanceof MethodArgumentNotValidException invalid && invalid.getBindingResult().getFieldError() != null) {
            var field = invalid.getBindingResult().getFieldError();
            message = field.getField() + ": " + field.getDefaultMessage();
        } else if (error instanceof MethodArgumentTypeMismatchException mismatch) {
            message = mismatch.getName() + ": invalid value";
        } else if (error instanceof MissingServletRequestParameterException missing) {
            message = missing.getParameterName() + ": is required";
        } else if (error instanceof MissingRequestHeaderException missingHeader) {
            message = missingHeader.getHeaderName() + " header is required";
        }
        return respond(ErrorCode.VALIDATION_ERROR, message, request);
    }

    /** Lock wait timeout/deadlock khi nhiều request tranh cùng hàng; trạng thái đã rollback nên thử lại an toàn. */
    @ExceptionHandler({CannotAcquireLockException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> handleLock(Exception error, HttpServletRequest request) {
        log.warn("Lock conflict on {}: {}", request.getRequestURI(), error.getClass().getSimpleName());
        return respond(ErrorCode.CONCURRENT_UPDATE, "Concurrent update, please retry", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException error, HttpServletRequest request) {
        return ResponseEntity.status(404).body(new ErrorResponse("NOT_FOUND", "Resource not found",
                Instant.now(), request.getRequestURI()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException error,
                                                      HttpServletRequest request) {
        return ResponseEntity.status(405).body(new ErrorResponse("METHOD_NOT_ALLOWED", "Method not allowed",
                Instant.now(), request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception error, HttpServletRequest request) {
        log.error("Unexpected error on {}", request.getRequestURI(), error);
        return respond(ErrorCode.INTERNAL_ERROR, "Unexpected error", request);
    }

    private static ResponseEntity<ErrorResponse> respond(ErrorCode code, String message, HttpServletRequest request) {
        return ResponseEntity.status(code.httpStatus())
                .body(new ErrorResponse(code.name(), message, Instant.now(), request.getRequestURI()));
    }
}
