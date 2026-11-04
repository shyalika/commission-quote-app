package com.example.commissionquote.mockvendor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;

class StartupTest {

    @Test
    void refusesToStartWithoutApiKey() {
        SpringApplication app = new SpringApplication(MockVendorApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);

        // An empty value is what vendor.api-key=${VENDOR_API_KEY:} resolves to when the variable is unset.
        assertThatThrownBy(() -> app.run("--vendor.api-key="))
                .hasRootCauseInstanceOf(BindValidationException.class)
                .rootCause().hasMessageContaining("apiKey");
    }
}
