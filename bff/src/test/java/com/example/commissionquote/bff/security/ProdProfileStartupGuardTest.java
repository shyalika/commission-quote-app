package com.example.commissionquote.bff.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.commissionquote.bff.BffApplication;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;

class ProdProfileStartupGuardTest {

    @ParameterizedTest(name = "profiles {0}")
    @ValueSource(strings = {"prod", "local,prod"})
    void appRefusesToStartUnderProd(String profiles) {
        SpringApplication app = new SpringApplication(BffApplication.class);

        // Every other setting is valid, so the guard is the only thing that can stop startup.
        assertThatThrownBy(() -> app.run(
                "--spring.profiles.active=" + profiles,
                "--server.port=0",
                "--vendor.base-url=http://localhost:8081",
                "--vendor.api-key=test-key"))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(ProdProfileStartupGuard.MESSAGE);
    }
}
