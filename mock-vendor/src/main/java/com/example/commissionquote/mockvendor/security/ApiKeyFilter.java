package com.example.commissionquote.mockvendor.security;

import com.example.commissionquote.mockvendor.config.VendorProperties;
import com.example.commissionquote.mockvendor.web.ErrorBody;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Rejects {@code /v1/**} requests without the correct {@code api-key} header,
 * before any controller logic runs. A missing key and a wrong key get the same
 * 401 response. The key is never logged.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);

    private final byte[] expectedKey;
    private final ObjectMapper objectMapper;

    public ApiKeyFilter(VendorProperties properties, ObjectMapper objectMapper) {
        this.expectedKey = properties.apiKey().getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String apiKey = request.getHeader("api-key");
        if (apiKey == null || !MessageDigest.isEqual(expectedKey, apiKey.getBytes(StandardCharsets.UTF_8))) {
            log.info("Rejected request with missing or invalid api-key");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ErrorBody.UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }
}
