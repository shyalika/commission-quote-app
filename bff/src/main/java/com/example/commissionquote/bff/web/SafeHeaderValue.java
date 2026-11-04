package com.example.commissionquote.bff.web;

import java.util.regex.Pattern;

/**
 * The rule for client supplied IDs we log or forward (X-Correlation-Id,
 * Idempotency-Key): 1 to 64 letters, digits, '.', '_' or '-'. A UUID matches.
 * Anything else, for example line breaks, could forge log lines or headers.
 */
final class SafeHeaderValue {

    static final String DESCRIPTION = "1 to 64 letters, digits, '.', '_' or '-'";

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private SafeHeaderValue() {
    }

    static boolean isSafe(String value) {
        return value != null && SAFE.matcher(value).matches();
    }
}
