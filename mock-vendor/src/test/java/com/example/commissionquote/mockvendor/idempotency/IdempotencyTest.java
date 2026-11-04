package com.example.commissionquote.mockvendor.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.commissionquote.mockvendor.MockVendorIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

class IdempotencyTest extends MockVendorIntegrationTest {

    @Test
    void sameKeyAndPayloadReturnsTheSameQuote() throws Exception {
        String key = newIdempotencyKey();

        String first = quoteId(VALID_BODY, key);
        String second = quoteId(VALID_BODY, key);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void sameAmountWithDifferentScaleCountsAsTheSamePayload() throws Exception {
        String key = newIdempotencyKey();

        String first = quoteId("{\"loanAmount\": 500000.00, \"loanTermInMonths\": 360, \"riskBand\": \"MEDIUM\"}", key);
        String second = quoteId("{\"loanAmount\": 500000, \"loanTermInMonths\": 360, \"riskBand\": \"MEDIUM\"}", key);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void sameKeyWithDifferentPayloadReturns409() throws Exception {
        String key = newIdempotencyKey();
        quoteId(VALID_BODY, key);

        String otherBody = "{\"loanAmount\": 250000.00, \"loanTermInMonths\": 360, \"riskBand\": \"MEDIUM\"}";

        assertThat(postQuote(otherBody).header("Idempotency-Key", key)).hasStatus(409)
                .bodyJson().extractingPath("$.code").isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
    }

    @Test
    void requestsWithoutKeyGetDifferentQuotes() throws Exception {
        String first = JsonPath.read(postQuote(VALID_BODY).exchange().getResponse().getContentAsString(), "$.quoteId");
        String second = JsonPath.read(postQuote(VALID_BODY).exchange().getResponse().getContentAsString(), "$.quoteId");

        assertThat(second).isNotEqualTo(first);
    }

    private String quoteId(String body, String key) throws Exception {
        var result = postQuote(body).header("Idempotency-Key", key).exchange();
        assertThat(result).hasStatusOk();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.quoteId");
    }
}
