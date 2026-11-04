package com.example.commissionquote.mockvendor;

import com.example.commissionquote.mockvendor.simulation.FailureSimulator;
import com.example.commissionquote.mockvendor.simulation.Outcome;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test replacement for the random simulator: returns whatever outcome the test
 * sets, NONE by default.
 */
public class ControllableFailureSimulator implements FailureSimulator {

    private volatile Outcome outcome = Outcome.NONE;

    public void willReturn(Outcome outcome) {
        this.outcome = outcome;
    }

    @Override
    public Outcome next() {
        return outcome;
    }

    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        ControllableFailureSimulator controllableFailureSimulator() {
            return new ControllableFailureSimulator();
        }
    }
}
