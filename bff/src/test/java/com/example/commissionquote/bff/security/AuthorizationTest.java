package com.example.commissionquote.bff.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.example.commissionquote.bff.quote.Quote;
import com.example.commissionquote.bff.quote.QuoteService;
import com.example.commissionquote.bff.web.QuoteController;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;

/** POST /api/v1/quotes needs a known token with the create:quotes scope. */
@WebMvcTest(QuoteController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("local")
class AuthorizationTest {

    private static final String VALID_BODY =
            "{\"loanAmount\": 500000.00, \"loanTermInMonths\": 360, \"riskBand\": \"MEDIUM\"}";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private QuoteService quoteService;

    @Test
    void noTokenReturns401NotRetryable() {
        var result = postQuote().exchange();

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.retryable").isEqualTo(false);
    }

    @Test
    void unknownTokenReturns401() {
        assertThat(postQuote().header("Authorization", "Bearer not-a-dev-token")).hasStatus(401);
    }

    @Test
    void devReadonlyWithoutScopeReturns403NotRetryable() {
        var result = postQuote().header("Authorization", "Bearer dev-readonly").exchange();

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.retryable").isEqualTo(false);
    }

    @Test
    void devStaffWithScopeReturns200() {
        given(quoteService.createQuote(any(), any(), any()))
                .willReturn(new Quote("q-1", new BigDecimal("0.0075"), new BigDecimal("3750.00")));

        assertThat(postQuote().header("Authorization", "Bearer dev-staff")).hasStatusOk();
    }

    private MockMvcRequestBuilder postQuote() {
        return mvc.post().uri("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY);
    }
}
