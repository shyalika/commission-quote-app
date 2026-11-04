package com.example.commissionquote.bff.web;

import com.example.commissionquote.bff.vendor.VendorCallException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Turns every error into a ProblemDetail with a generic title, {@code retryable}
 * and {@code correlationId}. The browser is never told what actually failed;
 * the cause is only logged.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final String INVALID_REQUEST = "Invalid request";
    private static final String VENDOR_FAILED = "Quote service unavailable";
    private static final String UNEXPECTED = "Unexpected error";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validationFailed(MethodArgumentNotValidException e) {
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> fieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        log.info("Validation failed fields={}", errors.stream().map(error -> error.get("field")).toList());
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, INVALID_REQUEST, false);
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadableBody(HttpMessageNotReadableException e) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, INVALID_REQUEST, false);
        if (e.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            String field = mismatch.getPath().getLast().getPropertyName();
            problem.setProperty("errors", List.of(fieldError(field, field + " " + typeMessage(mismatch.getTargetType()))));
            log.info("Wrong type for field={}", field);
        } else {
            problem.setDetail("Request body is not valid JSON");
            log.info("Unreadable request body");
        }
        return problem;
    }

    @ExceptionHandler(VendorCallException.class)
    ProblemDetail vendorCallFailed(VendorCallException e) {
        switch (e.reason()) {
            case UNAVAILABLE -> log.warn("Vendor unavailable vendorStatus={} cause={}", e.vendorStatus(),
                    e.getCause() == null ? null : e.getCause().getMessage());
            case UNAUTHORIZED -> log.error("Vendor rejected our api-key: configuration problem vendorStatus=401");
            case BAD_REQUEST -> log.error("Vendor rejected a request our validation accepted: bug vendorStatus=400");
            case REJECTED -> log.error("Vendor rejected the request vendorStatus={}", e.vendorStatus());
        }
        boolean retryable = e.reason() == VendorCallException.Reason.UNAVAILABLE;
        return problem(HttpStatus.BAD_GATEWAY, VENDOR_FAILED, retryable);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e) {
        // Spring's own errors (404, 405, 415, ...) keep their status. They are the
        // caller's mistake, so retrying would not help.
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            log.info("Request rejected status={} reason={}", status.value(), e.getClass().getSimpleName());
            HttpStatus known = HttpStatus.resolve(status.value());
            String title = known == null ? INVALID_REQUEST : known.getReasonPhrase();
            return ResponseEntity.status(status)
                    .headers(errorResponse.getHeaders())
                    .body(problem(status, title, false));
        }
        log.error("Unexpected error", e);
        return ResponseEntity.internalServerError().body(problem(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED, true));
    }

    private static ProblemDetail problem(HttpStatusCode status, String title, boolean retryable) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setProperty("retryable", retryable);
        problem.setProperty("correlationId", CorrelationIdFilter.current());
        return problem;
    }

    private static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", field, "message", message);
    }

    private static String typeMessage(Class<?> targetType) {
        if (targetType == Integer.class || targetType == int.class) {
            return "must be a whole number";
        }
        if (targetType == BigDecimal.class) {
            return "must be a number";
        }
        return "has an invalid value";
    }
}
