package com.example.commissionquote.bff.web;

import com.example.commissionquote.bff.quote.Quote;
import java.math.BigDecimal;

/** Response of {@code POST /api/v1/quotes}: the vendor's values, unchanged. */
public record QuoteResponse(String quoteId, BigDecimal commissionRate, BigDecimal totalCommission) {

    static QuoteResponse from(Quote quote) {
        return new QuoteResponse(quote.quoteId(), quote.commissionRate(), quote.totalCommission());
    }
}
