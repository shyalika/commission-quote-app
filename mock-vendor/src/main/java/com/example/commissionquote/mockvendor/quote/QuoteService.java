package com.example.commissionquote.mockvendor.quote;

import com.example.commissionquote.mockvendor.config.VendorProperties;
import com.example.commissionquote.mockvendor.idempotency.IdempotencyStore;
import com.example.commissionquote.mockvendor.simulation.FailureSimulator;
import com.example.commissionquote.mockvendor.simulation.Outcome;
import com.example.commissionquote.mockvendor.simulation.SimulatedFailureException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

    private final CommissionCalculator calculator;
    private final IdempotencyStore idempotencyStore;
    private final FailureSimulator failureSimulator;
    private final Duration slowDelay;

    public QuoteService(CommissionCalculator calculator, IdempotencyStore idempotencyStore,
            FailureSimulator failureSimulator, VendorProperties properties) {
        this.calculator = calculator;
        this.idempotencyStore = idempotencyStore;
        this.failureSimulator = failureSimulator;
        this.slowDelay = properties.simulation().slowDelay();
    }

    /**
     * Creates a quote, or returns the stored one for a repeated Idempotency-Key.
     *
     * @param idempotencyKey optional; null when the header was not sent
     * @param correlationId optional; only used for logging
     */
    public QuoteResponse createQuote(QuoteRequest request, String idempotencyKey, String correlationId) {
        // 1. A repeated key returns the stored quote (or 409 for a different payload).
        if (idempotencyKey != null) {
            Optional<QuoteResponse> stored = idempotencyStore.find(idempotencyKey, request);
            if (stored.isPresent()) {
                log.info("Returning stored quote quoteId={} correlationId={}", stored.get().quoteId(), correlationId);
                return stored.get();
            }
        }

        // 2. Simulated failures happen before anything is stored, so a retry can succeed.
        Outcome outcome = failureSimulator.next();
        if (outcome == Outcome.ERROR_500 || outcome == Outcome.ERROR_503) {
            int status = outcome == Outcome.ERROR_500 ? 500 : 503;
            log.warn("Simulated failure status={} correlationId={}", status, correlationId);
            throw new SimulatedFailureException(status);
        }

        // 3. Create the quote and store it under the key.
        QuoteResponse response = idempotencyKey == null
                ? newQuote(request)
                : idempotencyStore.getOrCreate(idempotencyKey, request, () -> newQuote(request));
        log.info("Created quote quoteId={} correlationId={}", response.quoteId(), correlationId);

        // 4. A slow response is delayed after storing, so a retry gets the same quote.
        if (outcome == Outcome.SLOW) {
            log.info("Simulating slow response delay={} correlationId={}", slowDelay, correlationId);
            sleep(slowDelay);
        }
        return response;
    }

    private QuoteResponse newQuote(QuoteRequest request) {
        BigDecimal rate = calculator.rateFor(request.riskBand());
        BigDecimal total = calculator.totalCommission(request.loanAmount(), rate);
        return new QuoteResponse(UUID.randomUUID().toString(), rate, total);
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
