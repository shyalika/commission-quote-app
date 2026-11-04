package com.example.commissionquote.mockvendor.quote;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.commissionquote.mockvendor.MockVendorIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CreateQuoteTest extends MockVendorIntegrationTest {

    @Test
    void validRequestReturnsQuoteWithRateAndTotal() throws Exception {
        var result = postQuote(VALID_BODY).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.quoteId").asString().isNotBlank();
        // Compare the raw JSON so the exact decimals are checked, not a parsed double.
        assertThat(result.getResponse().getContentAsString())
                .contains("\"commissionRate\":0.0075")
                .contains("\"totalCommission\":3750.00");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "{\"loanTermInMonths\": 360, \"riskBand\": \"MEDIUM\"}",
            "{\"loanAmount\": 0, \"loanTermInMonths\": 360, \"riskBand\": \"MEDIUM\"}",
            "{\"loanAmount\": -1, \"loanTermInMonths\": 360, \"riskBand\": \"MEDIUM\"}",
            "{\"loanAmount\": 500000, \"loanTermInMonths\": 0, \"riskBand\": \"MEDIUM\"}",
            "{\"loanAmount\": 500000, \"loanTermInMonths\": 12.5, \"riskBand\": \"MEDIUM\"}",
            "{\"loanAmount\": 500000, \"loanTermInMonths\": 360, \"riskBand\": \"medium\"}",
            "{\"loanAmount\": 500000, \"loanTermInMonths\": 360, \"riskBand\": \"EXTREME\"}",
            "{\"loanAmount\": 500000, \"loanTermInMonths\": 360, \"riskBand\": \"MEDIUM\", \"extra\": 1}",
            "{\"loanAmount\": \"500000\", \"loanTermInMonths\": 360, \"riskBand\": \"MEDIUM\"}",
            "{\"loanAmount\": 500000,"})
    void invalidPayloadReturns400(String body) {
        assertThat(postQuote(body)).hasStatus(400)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_REQUEST");
    }
}
