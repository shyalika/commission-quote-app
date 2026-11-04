package com.example.commissionquote.bff.web;

/**
 * A request header that is present but not in an accepted format. Mapped to 400.
 * Carries the header name only, never the client's value.
 */
class InvalidHeaderException extends RuntimeException {

    private final String header;

    InvalidHeaderException(String header) {
        super("Invalid " + header + " header");
        this.header = header;
    }

    String header() {
        return header;
    }
}
