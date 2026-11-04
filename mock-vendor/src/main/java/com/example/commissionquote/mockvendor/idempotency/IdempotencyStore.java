package com.example.commissionquote.mockvendor.idempotency;

import com.example.commissionquote.mockvendor.config.VendorProperties;
import com.example.commissionquote.mockvendor.quote.QuoteRequest;
import com.example.commissionquote.mockvendor.quote.QuoteResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * In memory store of Idempotency-Key to (request, response). Entries expire
 * after the configured TTL.
 */
@Component
public class IdempotencyStore {

    private record Entry(QuoteRequest request, QuoteResponse response, Instant createdAt) {
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    public IdempotencyStore(Clock clock, VendorProperties properties) {
        this.clock = clock;
        this.ttl = properties.idempotencyTtl();
    }

    /**
     * Returns the stored response for this key, or empty if there is none.
     *
     * @throws IdempotencyConflictException if the key was used with a different payload
     */
    public Optional<QuoteResponse> find(String key, QuoteRequest request) {
        Entry entry = entries.get(key);
        if (entry == null || isExpired(entry)) {
            return Optional.empty();
        }
        return Optional.of(responseFor(entry, request));
    }

    /**
     * Returns the stored response for this key, or creates one with
     * {@code create} and stores it. Creation is atomic per key, so two
     * concurrent requests with the same key get the same quote.
     *
     * @throws IdempotencyConflictException if the key was used with a different payload
     */
    public QuoteResponse getOrCreate(String key, QuoteRequest request, Supplier<QuoteResponse> create) {
        entries.values().removeIf(this::isExpired);
        Entry entry = entries.compute(key, (k, existing) ->
                existing != null && !isExpired(existing)
                        ? existing
                        : new Entry(request, create.get(), clock.instant()));
        return responseFor(entry, request);
    }

    private QuoteResponse responseFor(Entry entry, QuoteRequest request) {
        if (!entry.request().samePayloadAs(request)) {
            throw new IdempotencyConflictException();
        }
        return entry.response();
    }

    private boolean isExpired(Entry entry) {
        return !clock.instant().isBefore(entry.createdAt().plus(ttl));
    }
}
