package com.example.commissionquote.mockvendor.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Mock vendor configuration. Startup fails if {@code vendor.api-key} is blank.
 */
@Validated
@ConfigurationProperties("vendor")
public record VendorProperties(
        @NotBlank String apiKey,
        @NotNull Duration idempotencyTtl,
        @Valid @NotNull Simulation simulation) {

    public record Simulation(
            boolean enabled,
            @DecimalMin("0.0") @DecimalMax("1.0") double failureRate,
            @DecimalMin("0.0") @DecimalMax("1.0") double slowRate,
            @NotNull Duration slowDelay) {
    }

    @Override
    public String toString() {
        // Keeps the key out of logs and error messages.
        return "VendorProperties[apiKey=****, idempotencyTtl=" + idempotencyTtl + ", simulation=" + simulation + "]";
    }
}
