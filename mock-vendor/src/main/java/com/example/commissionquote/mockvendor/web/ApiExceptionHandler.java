package com.example.commissionquote.mockvendor.web;

import com.example.commissionquote.mockvendor.idempotency.IdempotencyConflictException;
import com.example.commissionquote.mockvendor.simulation.SimulatedFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns exceptions into the {@code Error} body from the contract. Messages are
 * generic; details stay in the logs.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ErrorBody> invalidRequest(Exception e) {
        log.info("Invalid request: {}", e.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorBody.INVALID_REQUEST);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<ErrorBody> idempotencyConflict() {
        log.info("Idempotency-Key reused with a different payload");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorBody.IDEMPOTENCY_KEY_CONFLICT);
    }

    @ExceptionHandler(SimulatedFailureException.class)
    ResponseEntity<ErrorBody> simulatedFailure(SimulatedFailureException e) {
        return e.status() == 503
                ? ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorBody.SERVICE_UNAVAILABLE)
                : ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorBody.INTERNAL_ERROR);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> unexpected(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorBody.INTERNAL_ERROR);
    }
}
