package com.example.commissionquote.mockvendor.simulation;

import com.example.commissionquote.mockvendor.config.VendorProperties;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

/**
 * Picks an outcome at random using the configured rates. Failures are split
 * evenly between 500 and 503.
 */
@Component
public class RandomFailureSimulator implements FailureSimulator {

    private final RandomGenerator random;
    private final VendorProperties.Simulation config;

    public RandomFailureSimulator(RandomGenerator random, VendorProperties properties) {
        this.random = random;
        this.config = properties.simulation();
    }

    @Override
    public Outcome next() {
        if (!config.enabled()) {
            return Outcome.NONE;
        }
        double r = random.nextDouble();
        if (r < config.failureRate()) {
            return r < config.failureRate() / 2 ? Outcome.ERROR_500 : Outcome.ERROR_503;
        }
        if (r < config.failureRate() + config.slowRate()) {
            return Outcome.SLOW;
        }
        return Outcome.NONE;
    }
}
