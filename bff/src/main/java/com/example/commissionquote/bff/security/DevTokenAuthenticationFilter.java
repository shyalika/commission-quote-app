package com.example.commissionquote.bff.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Local profile only. Maps the fixed dev bearer tokens to a principal:
 * {@code dev-staff} has {@code create:quotes}, {@code dev-readonly} has no scopes.
 * Any other token leaves the request anonymous, so protected endpoints return 401.
 * The header and the token are never logged.
 */
final class DevTokenAuthenticationFilter extends OncePerRequestFilter {

    static final String DEV_STAFF = "dev-staff";
    static final String DEV_READONLY = "dev-readonly";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final byte[] DEV_STAFF_BYTES = DEV_STAFF.getBytes(StandardCharsets.UTF_8);
    private static final byte[] DEV_READONLY_BYTES = DEV_READONLY.getBytes(StandardCharsets.UTF_8);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = authenticate(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (authentication != null) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }

    private static Authentication authenticate(String header) {
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        byte[] token = header.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
        if (MessageDigest.isEqual(token, DEV_STAFF_BYTES)) {
            return UsernamePasswordAuthenticationToken.authenticated(
                    DEV_STAFF, null, List.of(new SimpleGrantedAuthority(SecurityConfig.CREATE_QUOTES)));
        }
        if (MessageDigest.isEqual(token, DEV_READONLY_BYTES)) {
            return UsernamePasswordAuthenticationToken.authenticated(DEV_READONLY, null, List.of());
        }
        return null;
    }
}
