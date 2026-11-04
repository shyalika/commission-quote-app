package com.example.commissionquote.mockvendor.web;

/** The {@code Error} schema in docs/vendor-openapi.yaml. */
public record ErrorBody(String code, String message) {

    public static final ErrorBody INVALID_REQUEST =
            new ErrorBody("INVALID_REQUEST", "The request payload is invalid.");
    public static final ErrorBody UNAUTHORIZED =
            new ErrorBody("UNAUTHORIZED", "Missing or invalid API key.");
    public static final ErrorBody IDEMPOTENCY_KEY_CONFLICT =
            new ErrorBody("IDEMPOTENCY_KEY_CONFLICT", "Idempotency-Key was already used with a different payload.");
    public static final ErrorBody INTERNAL_ERROR =
            new ErrorBody("INTERNAL_ERROR", "An internal error occurred.");
    public static final ErrorBody SERVICE_UNAVAILABLE =
            new ErrorBody("SERVICE_UNAVAILABLE", "The service is temporarily unavailable.");
}
