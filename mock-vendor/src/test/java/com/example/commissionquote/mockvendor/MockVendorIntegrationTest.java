package com.example.commissionquote.mockvendor;

import com.example.commissionquote.mockvendor.simulation.Outcome;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;

/**
 * Base for tests that call the running app through MockMvc. The api-key filter
 * and exception handler are included; failures are controlled by the test.
 */
@SpringBootTest(properties = {
        "vendor.api-key=" + MockVendorIntegrationTest.API_KEY,
        "vendor.simulation.slow-delay=200ms"})
@AutoConfigureMockMvc
@Import(ControllableFailureSimulator.Config.class)
public abstract class MockVendorIntegrationTest {

    protected static final String API_KEY = "test-key";
    protected static final String PATH = "/v1/commission-quotes";
    protected static final String VALID_BODY =
            "{\"loanAmount\": 500000.00, \"loanTermInMonths\": 360, \"riskBand\": \"MEDIUM\"}";

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected ControllableFailureSimulator failureSimulator;

    @BeforeEach
    void resetFailureSimulator() {
        failureSimulator.willReturn(Outcome.NONE);
    }

    /** A POST with the correct api-key and the given JSON body. */
    protected MockMvcRequestBuilder postQuote(String body) {
        return mvc.post().uri(PATH)
                .header("api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    /** A fresh key, so tests sharing the app context never collide in the store. */
    protected static String newIdempotencyKey() {
        return UUID.randomUUID().toString();
    }
}
