package com.example.commissionquote.bff.quote;

import java.math.BigDecimal;

/** A validated request for a commission quote. */
public record QuoteRequest(BigDecimal loanAmount, int loanTermInMonths, RiskBand riskBand) {
}
