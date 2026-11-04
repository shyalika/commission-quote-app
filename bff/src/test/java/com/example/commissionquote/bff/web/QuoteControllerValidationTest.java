package com.example.commissionquote.bff.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.commissionquote.bff.quote.Quote;
import com.example.commissionquote.bff.quote.QuoteService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;

@WebMvcTest(QuoteController.class)
class QuoteControllerValidationTest {

    private static final String VALID_BODY =
            "{\"loanAmount\": 500000.00, \"loanTermInMonths\": 360, \"riskBand\": \"MEDIUM\"}";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private QuoteService quoteService;

    @Test
    void validRequestReturnsTheServiceQuote() throws Exception {
        given(quoteService.createQuote(any(), any()))
                .willReturn(new Quote("q-1", new BigDecimal("0.0075"), new BigDecimal("3750.00")));

        var result = post(VALID_BODY).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString())
                .isEqualTo("{\"quoteId\":\"q-1\",\"commissionRate\":0.0075,\"totalCommission\":3750.00}");
    }

    @ParameterizedTest(name = "{0} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"loanTermInMonths\": 360, \"riskBand\": \"MEDIUM\"}                               | loanAmount       | loanAmount is required",
            "{\"loanAmount\": 999.99, \"loanTermInMonths\": 360, \"riskBand\": \"MEDIUM\"}       | loanAmount       | loanAmount must be between 1,000 and 10,000,000",
            "{\"loanAmount\": -5, \"loanTermInMonths\": 360, \"riskBand\": \"MEDIUM\"}           | loanAmount       | loanAmount must be between 1,000 and 10,000,000",
            "{\"loanAmount\": 10000000.01, \"loanTermInMonths\": 360, \"riskBand\": \"MEDIUM\"}  | loanAmount       | loanAmount must be between 1,000 and 10,000,000",
            "{\"loanAmount\": 5000.123, \"loanTermInMonths\": 360, \"riskBand\": \"MEDIUM\"}     | loanAmount       | loanAmount must have at most 2 decimal places",
            "{\"loanAmount\": \"abc\", \"loanTermInMonths\": 360, \"riskBand\": \"MEDIUM\"}      | loanAmount       | loanAmount must be a number",
            "{\"loanAmount\": 500000, \"riskBand\": \"MEDIUM\"}                                  | loanTermInMonths | loanTermInMonths is required",
            "{\"loanAmount\": 500000, \"loanTermInMonths\": 0, \"riskBand\": \"MEDIUM\"}         | loanTermInMonths | loanTermInMonths must be between 1 and 360",
            "{\"loanAmount\": 500000, \"loanTermInMonths\": 361, \"riskBand\": \"MEDIUM\"}       | loanTermInMonths | loanTermInMonths must be between 1 and 360",
            "{\"loanAmount\": 500000, \"loanTermInMonths\": 12.5, \"riskBand\": \"MEDIUM\"}      | loanTermInMonths | loanTermInMonths must be a whole number",
            "{\"loanAmount\": 500000, \"loanTermInMonths\": 360}                                 | riskBand         | riskBand is required",
            "{\"loanAmount\": 500000, \"loanTermInMonths\": 360, \"riskBand\": \"medium\"}       | riskBand         | riskBand must be LOW, MEDIUM or HIGH",
            "{\"loanAmount\": 500000, \"loanTermInMonths\": 360, \"riskBand\": \"EXTREME\"}      | riskBand         | riskBand must be LOW, MEDIUM or HIGH"})
    void invalidFieldReturns400WithFieldMessage(String body, String field, String message) {
        var result = post(body).exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo(field);
        assertThat(result).bodyJson().extractingPath("$.errors[0].message").isEqualTo(message);
        verifyNoInteractions(quoteService);
    }

    @Test
    void malformedJsonReturns400() {
        assertThat(post("{\"loanAmount\": 500000,")).hasStatus(400)
                .bodyJson().extractingPath("$.detail").isEqualTo("Request body is not valid JSON");
    }

    @Test
    void validationProblemIsNotRetryableAndCarriesCorrelationId() {
        var result = post("{}").header("X-Correlation-Id", "corr-123").exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Invalid request");
        assertThat(result).bodyJson().extractingPath("$.retryable").isEqualTo(false);
        assertThat(result).bodyJson().extractingPath("$.correlationId").isEqualTo("corr-123");
    }

    @Test
    void getReturns405NotRetryable() {
        var result = mvc.get().uri("/api/v1/quotes").exchange();

        assertThat(result).hasStatus(405);
        assertThat(result).bodyJson().extractingPath("$.retryable").isEqualTo(false);
    }

    private MockMvcRequestBuilder post(String body) {
        return mvc.post().uri("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
