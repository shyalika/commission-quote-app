package com.example.commissionquote.bff.vendor;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Vendor connection settings, from VENDOR_BASE_URL and VENDOR_API_KEY.
 * Startup fails if either is missing.
 */
@Validated
@ConfigurationProperties("vendor")
public record VendorProperties(@NotBlank String baseUrl, @NotBlank String apiKey) {

    @Override
    public String toString() {
        // Keeps the key out of logs and error messages.
        return "VendorProperties[baseUrl=" + baseUrl + ", apiKey=****]";
    }
}
