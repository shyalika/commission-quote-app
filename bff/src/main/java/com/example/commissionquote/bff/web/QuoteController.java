package com.example.commissionquote.bff.web;

import com.example.commissionquote.bff.quote.QuoteService;
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

    /** Requires the create:quotes scope; see security.SecurityConfig. */
    @PostMapping("/api/v1/quotes")
    public QuoteResponse createQuote(
            @Valid @RequestBody CreateQuoteRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return QuoteResponse.from(quoteService.createQuote(
                request.toQuoteRequest(), idempotencyKey, CorrelationIdFilter.current()));
    }
}
