package com.example.commissionquote.bff.security;

import org.springframework.core.env.Environment;

/**
 * Refuses to start under the prod profile. Prod authentication (Auth0 JWTs and
 * the gateway API key) is not built yet, and the local auth stub must never run
 * under prod. Once prod auth exists, this narrows to "local together with prod".
 */
final class ProdProfileStartupGuard {

    static final String MESSAGE = "prod profile is not supported yet: prod authentication is not implemented, "
            + "and the local auth stub must never run under prod";

    private ProdProfileStartupGuard() {
    }

    static void check(Environment environment) {
        if (environment.matchesProfiles("prod")) {
            throw new IllegalStateException(MESSAGE);
        }
    }
}
