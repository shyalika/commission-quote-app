package com.example.commissionquote.mockvendor.simulation;

/**
 * Decides whether the next request fails, is slow, or succeeds normally.
 * An interface so tests can force each outcome.
 */
public interface FailureSimulator {

    Outcome next();
}
