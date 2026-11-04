package com.example.commissionquote.mockvendor.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.commissionquote.mockvendor.MockVendorIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class ApiKeyFilterTest extends MockVendorIntegrationTest {

    @Test
    void missingApiKeyReturns401() {
        assertThat(postWithoutKey()).hasStatus(401)
                .bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void wrongApiKeyReturns401() {
        assertThat(postWithKey("wrong-key")).hasStatus(401)
                .bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void missingAndWrongKeyGetTheSameResponseBody() throws Exception {
        String missing = postWithoutKey().getResponse().getContentAsString();
        String wrong = postWithKey("wrong-key").getResponse().getContentAsString();

        assertThat(wrong).isEqualTo(missing);
    }

    @Test
    void healthEndpointDoesNotNeedApiKey() {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
    }

    private MvcTestResult postWithoutKey() {
        return mvc.post().uri(PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY).exchange();
    }

    private MvcTestResult postWithKey(String apiKey) {
        return mvc.post().uri(PATH).header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY).exchange();
    }
}
