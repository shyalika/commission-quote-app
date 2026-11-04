package com.example.commissionquote.bff.vendor;

/**
 * A failed vendor call. The reason and status are for logs only; the browser
 * only ever sees a generic error.
 */
public class VendorCallException extends RuntimeException {

    public enum Reason {
        /** Vendor 5xx or connection error. Retried. */
        UNAVAILABLE,
        /** Connect or read timeout. Retried. */
        TIMEOUT,
        /** Not enough of the overall deadline left to start another attempt. */
        DEADLINE_EXCEEDED,
        /** Vendor 401: our api-key is wrong. */
        UNAUTHORIZED,
        /** Vendor 400: our validation let something through that the vendor rejects. */
        BAD_REQUEST,
        /** Any other vendor 4xx. */
        REJECTED
    }

    private final Reason reason;
    private final Integer vendorStatus;

    public VendorCallException(Reason reason, Integer vendorStatus) {
        this(reason, vendorStatus, null);
    }

    public VendorCallException(Reason reason, Integer vendorStatus, Throwable cause) {
        super("Vendor call failed: " + reason + (vendorStatus == null ? "" : " (status " + vendorStatus + ")"), cause);
        this.reason = reason;
        this.vendorStatus = vendorStatus;
    }

    public Reason reason() {
        return reason;
    }

    /** The vendor's HTTP status, or null if there was no response. */
    public Integer vendorStatus() {
        return vendorStatus;
    }
}
