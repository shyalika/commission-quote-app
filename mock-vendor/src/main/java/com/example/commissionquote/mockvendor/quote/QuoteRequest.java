package com.example.commissionquote.mockvendor.quote;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * JSON body of {@code POST /v1/commission-quotes}. The rules match the vendor
 * contract only; business bounds belong to the BFF. The Idempotency-Key and
 * X-Correlation-Id headers are passed separately.
 */
public record QuoteRequest(
        @NotNull @Positive BigDecimal loanAmount,
        @NotNull @Min(1) Integer loanTermInMonths,
        @NotNull RiskBand riskBand) {

    /**
     * True when both requests ask for the same quote. Amounts are compared by
     * value, so 500000 and 500000.00 count as the same payload.
     */
    public boolean samePayloadAs(QuoteRequest other) {
        return loanAmount.compareTo(other.loanAmount) == 0
                && loanTermInMonths.equals(other.loanTermInMonths)
                && riskBand == other.riskBand;
    }
}
