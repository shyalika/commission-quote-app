package com.example.commissionquote.mockvendor.quote;

import java.math.BigDecimal;

public record QuoteResponse(String quoteId, BigDecimal commissionRate, BigDecimal totalCommission) {
}
