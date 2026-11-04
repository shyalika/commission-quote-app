package com.example.commissionquote.mockvendor.simulation;

/**
 * A simulated vendor failure. Mapped to a 500 or 503 response by the exception
 * handler.
 */
public class SimulatedFailureException extends RuntimeException {

    private final int status;

    public SimulatedFailureException(int status) {
        super("Simulated failure with status " + status);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
