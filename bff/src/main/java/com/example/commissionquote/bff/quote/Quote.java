package com.example.commissionquote.bff.quote;

import java.math.BigDecimal;

/** A quote exactly as the vendor returned it. */
public record Quote(String quoteId, BigDecimal commissionRate, BigDecimal totalCommission) {
}
