package com.example.commissionquote.bff.vendor;

import com.example.commissionquote.bff.quote.Quote;
import com.example.commissionquote.bff.quote.QuoteRequest;
import com.example.commissionquote.bff.vendor.VendorCallException.Reason;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls {@code POST /v1/commission-quotes} as described in docs/vendor-openapi.yaml.
 *
 * <p>Each attempt has connect and read timeouts. Vendor 5xx, timeouts and
 * connection errors are retried with exponential backoff and full jitter; 4xx
 * never is. No attempt starts unless it can finish within the overall deadline.
 * Every attempt sends the same Idempotency-Key, so retries never create
 * duplicate quotes. Never logs the api-key or the bodies.
 */
@Component
@EnableConfigurationProperties(VendorProperties.class)
public class HttpVendorQuoteClient implements VendorQuoteClient {

    private static final Logger log = LoggerFactory.getLogger(HttpVendorQuoteClient.class);

    /** The vendor's request body. */
    private record VendorRequest(BigDecimal loanAmount, int loanTermInMonths, String riskBand) {
    }

    /** The vendor's 200 response body. */
    private record VendorResponse(String quoteId, BigDecimal commissionRate, BigDecimal totalCommission) {
    }

    private final RestClient restClient;
    private final String apiKey;
    private final Retry retry;
    private final Duration deadline;
    /** The longest one attempt can take: connect plus read timeout. */
    private final Duration attemptBudget;

    /**
     * Uses Spring's configured builder, so anything Spring adds to it (for
     * example Micrometer instrumentation) applies to vendor calls too.
     */
    public HttpVendorQuoteClient(RestClient.Builder restClientBuilder, VendorProperties properties) {
        // HTTP/1.1: on plain http the JDK client would try an HTTP/2 upgrade, which
        // some servers (WireMock's Jetty) reject on a POST by closing the connection.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        this.apiKey = properties.apiKey();
        this.deadline = properties.deadline();
        this.attemptBudget = properties.connectTimeout().plus(properties.readTimeout());
        this.retry = Retry.of("vendor", RetryConfig.custom()
                .maxAttempts(properties.retry().maxAttempts())
                .intervalFunction(fullJitter(properties.retry()))
                .retryOnException(HttpVendorQuoteClient::isRetryable)
                .build());
        retry.getEventPublisher().onRetry(event -> log.warn("Retrying vendor call retry={} reason={}",
                event.getNumberOfRetryAttempts(), reasonOf(event.getLastThrowable())));
    }

    @Override
    public Quote createQuote(QuoteRequest request, String idempotencyKey, String correlationId) {
        VendorRequest body = new VendorRequest(
                request.loanAmount(), request.loanTermInMonths(), request.riskBand().name());
        long start = System.nanoTime();
        AtomicInteger attempts = new AtomicInteger();
        return retry.executeSupplier(() -> {
            if (attempts.getAndIncrement() > 0) {
                checkDeadline(start);
            }
            return callOnce(body, idempotencyKey, correlationId);
        });
    }

    private Quote callOnce(VendorRequest body, String idempotencyKey, String correlationId) {
        VendorResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/commission-quotes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("api-key", apiKey)
                    .header("Idempotency-Key", idempotencyKey)
                    .header("X-Correlation-Id", correlationId)
                    .body(body)
                    .retrieve()
                    .body(VendorResponse.class);
        } catch (RestClientResponseException e) {
            // The exception message contains the vendor's body, so only the status is kept.
            int status = e.getStatusCode().value();
            throw new VendorCallException(reasonFor(status), status);
        } catch (ResourceAccessException e) {
            // A connection error's message holds the URL and the I/O error, no body or key.
            throw new VendorCallException(isTimeout(e) ? Reason.TIMEOUT : Reason.UNAVAILABLE, null, e);
        }
        return new Quote(response.quoteId(), response.commissionRate(), response.totalCommission());
    }

    /** No new attempt starts unless it can finish within the overall deadline. */
    private void checkDeadline(long startNanos) {
        Duration remaining = deadline.minus(Duration.ofNanos(System.nanoTime() - startNanos));
        if (remaining.compareTo(attemptBudget) < 0) {
            throw new VendorCallException(Reason.DEADLINE_EXCEEDED, null);
        }
    }

    /**
     * Full jitter: a random wait between 0 and the exponential backoff for this
     * retry, capped at maxBackoff. Spreads retries out so clients don't retry in step.
     */
    private static IntervalFunction fullJitter(VendorProperties.Retry config) {
        long initial = config.initialBackoff().toMillis();
        long cap = config.maxBackoff().toMillis();
        return retryNumber -> {
            long backoff = (long) Math.min(cap, initial * Math.pow(config.multiplier(), retryNumber - 1));
            return ThreadLocalRandom.current().nextLong(backoff + 1);
        };
    }

    private static boolean isRetryable(Throwable e) {
        return e instanceof VendorCallException vendorError
                && (vendorError.reason() == Reason.UNAVAILABLE || vendorError.reason() == Reason.TIMEOUT);
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException
                    || cause instanceof SocketTimeoutException
                    || cause instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static Object reasonOf(Throwable e) {
        return e instanceof VendorCallException vendorError ? vendorError.reason() : e.getClass().getSimpleName();
    }

    private static Reason reasonFor(int status) {
        if (status >= 500) {
            return Reason.UNAVAILABLE;
        }
        return switch (status) {
            case 401 -> Reason.UNAUTHORIZED;
            case 400 -> Reason.BAD_REQUEST;
            default -> Reason.REJECTED;
        };
    }
}
