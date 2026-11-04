package com.example.commissionquote.bff.vendor;

import com.example.commissionquote.bff.quote.Quote;
import com.example.commissionquote.bff.quote.QuoteRequest;

/** Gets a quote from the vendor Commission Quote API. */
public interface VendorQuoteClient {

    /**
     * @throws VendorCallException if the vendor call fails
     */
    Quote createQuote(QuoteRequest request, String correlationId);
}
