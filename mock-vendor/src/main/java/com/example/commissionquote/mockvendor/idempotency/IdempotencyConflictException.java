package com.example.commissionquote.mockvendor.idempotency;

/**
 * The Idempotency-Key was already used with a different payload. Mapped to a
 * 409 response by the exception handler.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("Idempotency-Key was already used with a different payload");
    }
}
