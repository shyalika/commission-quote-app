package com.example.commissionquote.mockvendor.quote;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @PostMapping("/v1/commission-quotes")
    public QuoteResponse createQuote(
            @Valid @RequestBody QuoteRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId) {
        return quoteService.createQuote(request, idempotencyKey, safeForLogs(correlationId));
    }

    /** Keeps a client supplied header from forging extra log lines. */
    private static String safeForLogs(String value) {
        return value == null ? null : value.replaceAll("[\\r\\n\\t]", "_");
    }
}
