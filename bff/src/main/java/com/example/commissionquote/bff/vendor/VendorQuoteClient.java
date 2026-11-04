package com.example.commissionquote.bff.vendor;

import com.example.commissionquote.bff.quote.Quote;
import com.example.commissionquote.bff.quote.QuoteRequest;

/** Gets a quote from the vendor Commission Quote API. */
public interface VendorQuoteClient {

    /**
     * @param idempotencyKey sent on every attempt, so retries never create duplicate quotes
     * @throws VendorCallException if the vendor call fails
     */
    Quote createQuote(QuoteRequest request, String idempotencyKey, String correlationId);
}
