package com.example.commissionquote.mockvendor.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.commissionquote.mockvendor.config.VendorProperties;
import java.time.Duration;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RandomFailureSimulatorTest {

    @Test
    void disabledSimulationAlwaysReturnsNone() {
        RandomFailureSimulator simulator = simulator(false, 0.0);

        assertThat(simulator.next()).isEqualTo(Outcome.NONE);
    }

    // failure-rate 0.2 (0.0-0.1 gives 500, 0.1-0.2 gives 503), slow-rate 0.1 (0.2-0.3), otherwise NONE.
    // 0.30 itself is avoided: 0.2 + 0.1 is slightly above 0.3 in double, and the
    // exact edge of a probability doesn't matter.
    @ParameterizedTest(name = "random {0} gives {1}")
    @CsvSource({
            "0.00, ERROR_500",
            "0.09, ERROR_500",
            "0.10, ERROR_503",
            "0.19, ERROR_503",
            "0.20, SLOW",
            "0.29, SLOW",
            "0.31, NONE",
            "0.99, NONE"})
    void mapsRandomValueToOutcomeUsingConfiguredRates(double randomValue, Outcome expected) {
        assertThat(simulator(true, randomValue).next()).isEqualTo(expected);
    }

    private static RandomFailureSimulator simulator(boolean enabled, double randomValue) {
        RandomGenerator fixed = new RandomGenerator() {
            @Override
            public long nextLong() {
                throw new UnsupportedOperationException();
            }

            @Override
            public double nextDouble() {
                return randomValue;
            }
        };
        var simulation = new VendorProperties.Simulation(enabled, 0.2, 0.1, Duration.ofSeconds(7));
        return new RandomFailureSimulator(fixed, new VendorProperties("key", Duration.ofHours(1), simulation));
    }
}
