package com.example.commissionquote.bff.quote;

import com.example.commissionquote.bff.vendor.VendorQuoteClient;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Gets a commission quote from the vendor. The vendor owns the calculation, so
 * its values are passed through unchanged, never recalculated.
 */
@Service
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

    private final VendorQuoteClient vendorQuoteClient;

    public QuoteService(VendorQuoteClient vendorQuoteClient) {
        this.vendorQuoteClient = vendorQuoteClient;
    }

    /**
     * @param idempotencyKey the caller's key, or null; one is generated if absent
     */
    public Quote createQuote(QuoteRequest request, String idempotencyKey, String correlationId) {
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? UUID.randomUUID().toString() : idempotencyKey;
        Quote quote = vendorQuoteClient.createQuote(request, key, correlationId);
        log.info("Quote generated quoteId={}", quote.quoteId());
        return quote;
    }
}
