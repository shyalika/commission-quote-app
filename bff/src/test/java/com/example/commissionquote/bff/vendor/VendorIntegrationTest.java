package com.example.commissionquote.bff.vendor;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The whole BFF against a WireMock vendor. */
@ActiveProfiles("local")
@SpringBootTest(properties = "vendor.api-key=" + VendorIntegrationTest.API_KEY)
@AutoConfigureMockMvc
class VendorIntegrationTest {

    static final String API_KEY = "test-vendor-key";
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
    void successPassesVendorValuesThroughUnchanged() throws Exception {
        vendor.stubFor(post(VENDOR_PATH).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"quoteId\":\"vendor-q-1\",\"commissionRate\":0.0075,\"totalCommission\":3750.00}")));

        MvcTestResult result = postQuote();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString())
                .isEqualTo("{\"quoteId\":\"vendor-q-1\",\"commissionRate\":0.0075,\"totalCommission\":3750.00}");
    }

    @Test
    void vendorCallCarriesApiKeyAndCorrelationId() {
        vendor.stubFor(post(VENDOR_PATH).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"quoteId\":\"q\",\"commissionRate\":0.0075,\"totalCommission\":3750.00}")));

        mvc.post().uri("/api/v1/quotes").header("Authorization", "Bearer dev-staff").contentType(MediaType.APPLICATION_JSON).content(BODY)
                .header("X-Correlation-Id", "corr-abc").exchange();

        vendor.verify(postRequestedFor(urlEqualTo(VENDOR_PATH))
                .withHeader("api-key", equalTo(API_KEY))
                .withHeader("X-Correlation-Id", equalTo("corr-abc")));
    }

    @Test
    void vendorReceivesLoanAmountAsPlainNumber() {
        vendor.stubFor(post(VENDOR_PATH).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"quoteId\":\"q\",\"commissionRate\":0.0075,\"totalCommission\":7500.00}")));

        // 1e6 parses to BigDecimal 1E+6, which Jackson writes as 1E+6 unless the
        // shared write-bigdecimal-as-plain setting reaches the vendor client.
        mvc.post().uri("/api/v1/quotes").header("Authorization", "Bearer dev-staff").contentType(MediaType.APPLICATION_JSON)
                .content("{\"loanAmount\": 1e6, \"loanTermInMonths\": 360, \"riskBand\": \"MEDIUM\"}").exchange();

        vendor.verify(postRequestedFor(urlEqualTo(VENDOR_PATH))
                .withRequestBody(containing("\"loanAmount\":1000000")));
    }

    @Test
    void vendor401Returns502NotRetryableWithoutLeakingDetail() throws Exception {
        vendor.stubFor(post(VENDOR_PATH).willReturn(aResponse().withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid API key.\"}")));

        MvcTestResult result = postQuote();

        assertThat(result).hasStatus(502);
        assertThat(result).bodyJson().extractingPath("$.retryable").isEqualTo(false);
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("401")
                .doesNotContainIgnoringCase("unauthorized")
                .doesNotContainIgnoringCase("api key")
                .doesNotContain(API_KEY);
    }

    @Test
    void vendor400Returns502NotRetryable() {
        vendor.stubFor(post(VENDOR_PATH).willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"INVALID_REQUEST\",\"message\":\"The request payload is invalid.\"}")));

        MvcTestResult result = postQuote();

        assertThat(result).hasStatus(502);
        assertThat(result).bodyJson().extractingPath("$.retryable").isEqualTo(false);
    }

    private MvcTestResult postQuote() {
        return mvc.post().uri("/api/v1/quotes").header("Authorization", "Bearer dev-staff").contentType(MediaType.APPLICATION_JSON).content(BODY).exchange();
    }
}
