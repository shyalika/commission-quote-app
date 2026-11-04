package com.example.commissionquote.bff.web;

import com.example.commissionquote.bff.quote.QuoteService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QuoteController {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    /** Requires the create:quotes scope; see security.SecurityConfig. */
    @PostMapping("/api/v1/quotes")
    public QuoteResponse createQuote(
            @Valid @RequestBody CreateQuoteRequest request,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            Principal principal) {
        // Absent or blank means "generate one"; anything else must be safe to forward to the vendor.
        if (idempotencyKey != null && !idempotencyKey.isBlank() && !SafeHeaderValue.isSafe(idempotencyKey)) {
            throw new InvalidHeaderException(IDEMPOTENCY_KEY_HEADER);
        }
        return QuoteResponse.from(quoteService.createQuote(
                request.toQuoteRequest(), idempotencyKey, CorrelationIdFilter.current(), principal.getName()));
    }
}
