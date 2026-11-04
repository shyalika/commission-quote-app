package com.example.commissionquote.bff.web;

import com.example.commissionquote.bff.quote.QuoteRequest;
import com.example.commissionquote.bff.quote.RiskBand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/** Body of {@code POST /api/v1/quotes}, with the rules from design section 6. */
public record CreateQuoteRequest(
        @NotNull(message = "loanAmount is required")
        @DecimalMin(value = "1000", message = "loanAmount must be between 1,000 and 10,000,000")
        @DecimalMax(value = "10000000", message = "loanAmount must be between 1,000 and 10,000,000")
        @Digits(integer = 8, fraction = 2, message = "loanAmount must have at most 2 decimal places")
        BigDecimal loanAmount,

        @NotNull(message = "loanTermInMonths is required")
        @Min(value = 1, message = "loanTermInMonths must be between 1 and 360")
        @Max(value = 360, message = "loanTermInMonths must be between 1 and 360")
        Integer loanTermInMonths,

        // A String, so a wrong value gets a field message rather than a parse error.
        @NotNull(message = "riskBand is required")
        @Pattern(regexp = "LOW|MEDIUM|HIGH", message = "riskBand must be LOW, MEDIUM or HIGH")
        String riskBand) {

    public QuoteRequest toQuoteRequest() {
        return new QuoteRequest(loanAmount, loanTermInMonths, RiskBand.valueOf(riskBand));
    }
}
