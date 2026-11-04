package com.example.commissionquote.mockvendor.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CommissionCalculatorTest {

    private final CommissionCalculator calculator = new CommissionCalculator();

    @ParameterizedTest(name = "{0}: rate {1}, total {2}")
    @CsvSource({
            "LOW,    0.0050, 2500.00",
            "MEDIUM, 0.0075, 3750.00",
            "HIGH,   0.0100, 5000.00"})
    void calculatesRateAndTotalForEachRiskBand(RiskBand band, String expectedRate, String expectedTotal) {
        BigDecimal rate = calculator.rateFor(band);
        BigDecimal total = calculator.totalCommission(new BigDecimal("500000.00"), rate);

        assertThat(rate).isEqualTo(expectedRate);
        assertThat(total).isEqualTo(expectedTotal);
    }

    @Test
    void roundsTotalHalfEvenToTwoDecimalPlaces() {
        // 1001.00 x 0.0050 = 5.005: HALF_EVEN gives 5.00, HALF_UP would give 5.01.
        BigDecimal total = calculator.totalCommission(new BigDecimal("1001.00"), calculator.rateFor(RiskBand.LOW));

        assertThat(total).isEqualTo("5.00");
    }
}
