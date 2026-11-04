package com.example.commissionquote.mockvendor.simulation;

/** What the mock does with the next request. */
public enum Outcome {
    NONE,
    ERROR_500,
    ERROR_503,
    SLOW
}
