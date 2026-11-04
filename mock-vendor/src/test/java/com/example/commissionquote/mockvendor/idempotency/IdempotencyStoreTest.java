package com.example.commissionquote.mockvendor.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.commissionquote.mockvendor.config.VendorProperties;
import com.example.commissionquote.mockvendor.quote.QuoteRequest;
import com.example.commissionquote.mockvendor.quote.QuoteResponse;
import com.example.commissionquote.mockvendor.quote.RiskBand;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class IdempotencyStoreTest {

    private static final Duration TTL = Duration.ofHours(1);
    private static final QuoteRequest REQUEST = new QuoteRequest(new BigDecimal("500000.00"), 360, RiskBand.MEDIUM);
    private static final QuoteResponse RESPONSE =
            new QuoteResponse("quote-1", new BigDecimal("0.0075"), new BigDecimal("3750.00"));

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final IdempotencyStore store = new IdempotencyStore(clock,
            new VendorProperties("key", TTL, new VendorProperties.Simulation(false, 0, 0, Duration.ZERO)));

    @Test
    void storedResponseIsReturnedWithinTheTtl() {
        store.getOrCreate("k", REQUEST, () -> RESPONSE);
        clock.advance(TTL.minusSeconds(1));

        assertThat(store.find("k", REQUEST)).contains(RESPONSE);
    }

    @Test
    void storedResponseIsGoneAfterTheTtl() {
        store.getOrCreate("k", REQUEST, () -> RESPONSE);
        clock.advance(TTL);

        assertThat(store.find("k", REQUEST)).isEmpty();
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
