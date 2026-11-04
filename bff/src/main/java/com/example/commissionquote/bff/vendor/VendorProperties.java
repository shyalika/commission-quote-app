package com.example.commissionquote.bff.vendor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Vendor connection settings. The base URL and api-key come from VENDOR_BASE_URL
 * and VENDOR_API_KEY; startup fails if either is missing.
 */
@Validated
@ConfigurationProperties("vendor")
public record VendorProperties(
        @NotBlank String baseUrl,
        @NotBlank String apiKey,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull Duration deadline,
        @Valid @NotNull Retry retry) {

    /** Exponential backoff with full jitter, capped at maxBackoff. */
    public record Retry(
            @Min(1) int maxAttempts,
            @NotNull Duration initialBackoff,
            @DecimalMin("1.0") double multiplier,
            @NotNull Duration maxBackoff) {
    }

    @Override
    public String toString() {
        // Keeps the key out of logs and error messages.
        return "VendorProperties[baseUrl=" + baseUrl + ", apiKey=****, connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + ", deadline=" + deadline + ", retry=" + retry + "]";
    }
}
