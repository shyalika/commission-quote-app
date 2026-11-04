package com.example.commissionquote.mockvendor.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.commissionquote.mockvendor.MockVendorIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class FailureSimulationTest extends MockVendorIntegrationTest {

    private static final Duration SLOW_DELAY = Duration.ofMillis(200);

    @Test
    void forcedError500Returns500() {
        failureSimulator.willReturn(Outcome.ERROR_500);

        assertThat(postQuote(VALID_BODY)).hasStatus(500)
                .bodyJson().extractingPath("$.code").isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void forcedError503Returns503() {
        failureSimulator.willReturn(Outcome.ERROR_503);

        assertThat(postQuote(VALID_BODY)).hasStatus(503)
                .bodyJson().extractingPath("$.code").isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void failedAttemptStoresNothingSoRetryWithSameKeySucceeds() {
        String key = newIdempotencyKey();
        failureSimulator.willReturn(Outcome.ERROR_503);
        assertThat(postQuote(VALID_BODY).header("Idempotency-Key", key)).hasStatus(503);

        failureSimulator.willReturn(Outcome.NONE);

        assertThat(postQuote(VALID_BODY).header("Idempotency-Key", key)).hasStatusOk();
    }

    @Test
    void forcedSlowResponseIsDelayedThenReturns200() {
        failureSimulator.willReturn(Outcome.SLOW);

        long start = System.nanoTime();
        var result = postQuote(VALID_BODY).exchange();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(result).hasStatusOk();
        assertThat(elapsed).isGreaterThanOrEqualTo(SLOW_DELAY);
    }

    @Test
    void slowResponseStoresQuoteSoRetryWithSameKeyGetsSameQuote() throws Exception {
        String key = newIdempotencyKey();
        failureSimulator.willReturn(Outcome.SLOW);
        String first = quoteId(key);

        failureSimulator.willReturn(Outcome.NONE);
        String retry = quoteId(key);

        assertThat(retry).isEqualTo(first);
    }

    private String quoteId(String key) throws Exception {
        var result = postQuote(VALID_BODY).header("Idempotency-Key", key).exchange();
        assertThat(result).hasStatusOk();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.quoteId");
    }
}
