package com.example.commissionquote.mockvendor.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Illustrative rates; the real vendor owns the calculation.
 */
@Component
public class CommissionCalculator {

    public BigDecimal rateFor(RiskBand riskBand) {
        return switch (riskBand) {
            case LOW -> new BigDecimal("0.0050");
            case MEDIUM -> new BigDecimal("0.0075");
            case HIGH -> new BigDecimal("0.0100");
        };
    }

    public BigDecimal totalCommission(BigDecimal loanAmount, BigDecimal rate) {
        return loanAmount.multiply(rate).setScale(2, RoundingMode.HALF_EVEN);
    }
}
