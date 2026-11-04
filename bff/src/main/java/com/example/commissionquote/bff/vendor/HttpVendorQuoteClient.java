package com.example.commissionquote.bff.vendor;

import com.example.commissionquote.bff.quote.Quote;
import com.example.commissionquote.bff.quote.QuoteRequest;
import com.example.commissionquote.bff.vendor.VendorCallException.Reason;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls {@code POST /v1/commission-quotes} as described in docs/vendor-openapi.yaml.
 * Sends the api-key and the correlation ID. Never logs the key or the bodies.
 */
@Component
@EnableConfigurationProperties(VendorProperties.class)
public class HttpVendorQuoteClient implements VendorQuoteClient {

    /** The vendor's request body. */
    private record VendorRequest(BigDecimal loanAmount, int loanTermInMonths, String riskBand) {
    }

    /** The vendor's 200 response body. */
    private record VendorResponse(String quoteId, BigDecimal commissionRate, BigDecimal totalCommission) {
    }

    private final RestClient restClient;
    private final String apiKey;

    /**
     * Uses Spring's configured builder, so anything Spring adds to it (for
     * example Micrometer instrumentation) applies to vendor calls too.
     */
    public HttpVendorQuoteClient(RestClient.Builder restClientBuilder, VendorProperties properties) {
        // HTTP/1.1: on plain http the JDK client would try an HTTP/2 upgrade, which
        // some servers (WireMock's Jetty) reject on a POST by closing the connection.
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
        this.apiKey = properties.apiKey();
    }

    @Override
    public Quote createQuote(QuoteRequest request, String correlationId) {
        VendorRequest body = new VendorRequest(
                request.loanAmount(), request.loanTermInMonths(), request.riskBand().name());
        VendorResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/commission-quotes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("api-key", apiKey)
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
            throw new VendorCallException(Reason.UNAVAILABLE, null, e);
        }
        return new Quote(response.quoteId(), response.commissionRate(), response.totalCommission());
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
