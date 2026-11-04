package com.example.commissionquote.mockvendor;

import static org.assertj.core.api.Assertions.assertThat;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import com.example.commissionquote.mockvendor.simulation.Outcome;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Checks real responses from the mock against docs/vendor-openapi.yaml, so the
 * mock cannot drift from the contract.
 */
class OpenApiContractTest extends MockVendorIntegrationTest {

    private static final String SPEC = Path.of("../docs/vendor-openapi.yaml").toAbsolutePath().toString();
    private static final OpenApiInteractionValidator VALIDATOR =
            OpenApiInteractionValidator.createForSpecificationUrl(SPEC).build();

    @Test
    void successResponseMatchesContract() {
        assertMatchesContract(postQuote(VALID_BODY).exchange(), 200);
    }

    @Test
    void invalidPayloadResponseMatchesContract() {
        assertMatchesContract(postQuote("{}").exchange(), 400);
    }

    @Test
    void unauthorizedResponseMatchesContract() {
        var result = mvc.post().uri(PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY).exchange();

        assertMatchesContract(result, 401);
    }

    @Test
    void idempotencyConflictResponseMatchesContract() {
        String key = newIdempotencyKey();
        postQuote(VALID_BODY).header("Idempotency-Key", key).exchange();
        String otherBody = "{\"loanAmount\": 1.00, \"loanTermInMonths\": 1, \"riskBand\": \"LOW\"}";

        assertMatchesContract(postQuote(otherBody).header("Idempotency-Key", key).exchange(), 409);
    }

    @Test
    void internalErrorResponseMatchesContract() {
        failureSimulator.willReturn(Outcome.ERROR_500);

        assertMatchesContract(postQuote(VALID_BODY).exchange(), 500);
    }

    @Test
    void serviceUnavailableResponseMatchesContract() {
        failureSimulator.willReturn(Outcome.ERROR_503);

        assertMatchesContract(postQuote(VALID_BODY).exchange(), 503);
    }

    private static void assertMatchesContract(MvcTestResult result, int expectedStatus) {
        MockHttpServletResponse response = result.getResponse();
        assertThat(response.getStatus()).isEqualTo(expectedStatus);

        SimpleResponse.Builder contractResponse = SimpleResponse.Builder.status(response.getStatus())
                .withContentType(response.getContentType());
        try {
            contractResponse.withBody(response.getContentAsString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        ValidationReport report = VALIDATOR.validateResponse(PATH, Request.Method.POST, contractResponse.build());

        assertThat(report.getMessages()).as("contract violations").isEmpty();
    }
}
