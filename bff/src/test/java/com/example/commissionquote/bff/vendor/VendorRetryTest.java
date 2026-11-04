package com.example.commissionquote.bff.vendor;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;

/** Retries, timeouts and Idempotency-Key against a WireMock vendor. Short timings keep it fast. */
@ActiveProfiles("local")
@SpringBootTest(properties = {
        "vendor.api-key=test-vendor-key",
        "vendor.connect-timeout=300ms",
        "vendor.read-timeout=300ms",
        "vendor.retry.initial-backoff=10ms",
        "vendor.retry.max-backoff=20ms"})
@AutoConfigureMockMvc
class VendorRetryTest {

    private static final String VENDOR_PATH = "/v1/commission-quotes";
    private static final String BODY =
            "{\"loanAmount\": 500000.00, \"loanTermInMonths\": 360, \"riskBand\": \"MEDIUM\"}";

    private static final WireMockServer vendor = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        vendor.start();
    }

    @DynamicPropertySource
    static void vendorBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("vendor.base-url", vendor::baseUrl);
    }

    @AfterAll
    static void stopVendor() {
        vendor.stop();
    }

    @Autowired
    private MockMvcTester mvc;

    @BeforeEach
    void resetVendor() {
        vendor.resetAll();
    }

    @Test
    void vendor503ThenSuccessReturns200() {
        firstThen(aResponse().withStatus(503), success());

        assertThat(postQuote()).hasStatusOk();
        vendor.verify(exactly(2), postRequestedFor(urlEqualTo(VENDOR_PATH)));
    }

    @Test
    void repeated503Returns502Retryable() {
        vendor.stubFor(post(VENDOR_PATH).willReturn(aResponse().withStatus(503)));

        var result = postQuote().exchange();

        assertThat(result).hasStatus(502);
        assertThat(result).bodyJson().extractingPath("$.retryable").isEqualTo(true);
        vendor.verify(exactly(3), postRequestedFor(urlEqualTo(VENDOR_PATH)));
    }

    @Test
    void slowResponseTimesOutAndRetryCarriesSameIdempotencyKey() {
        // The first response takes longer than the 300ms read timeout.
        firstThen(success().withFixedDelay(1000), success());

        assertThat(postQuote().header("Idempotency-Key", "key-1")).hasStatusOk();
        vendor.verify(exactly(2), postRequestedFor(urlEqualTo(VENDOR_PATH))
                .withHeader("Idempotency-Key", equalTo("key-1")));
    }

    @ParameterizedTest(name = "vendor {0}")
    @ValueSource(ints = {400, 401, 409})
    void vendor4xxIsNeverRetried(int status) {
        vendor.stubFor(post(VENDOR_PATH).willReturn(aResponse().withStatus(status)));

        assertThat(postQuote()).hasStatus(502);
        vendor.verify(exactly(1), postRequestedFor(urlEqualTo(VENDOR_PATH)));
    }

    /** The first vendor call gets {@code first}, every later call gets {@code then}. */
    private static void firstThen(ResponseDefinitionBuilder first, ResponseDefinitionBuilder then) {
        vendor.stubFor(post(VENDOR_PATH).inScenario("retry").whenScenarioStateIs(STARTED)
                .willReturn(first).willSetStateTo("retried"));
        vendor.stubFor(post(VENDOR_PATH).inScenario("retry").whenScenarioStateIs("retried")
                .willReturn(then));
    }

    private static ResponseDefinitionBuilder success() {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"quoteId\":\"q-1\",\"commissionRate\":0.0075,\"totalCommission\":3750.00}");
    }

    private MockMvcRequestBuilder postQuote() {
        return mvc.post().uri("/api/v1/quotes").header("Authorization", "Bearer dev-staff")
                .contentType(MediaType.APPLICATION_JSON).content(BODY);
    }
}
