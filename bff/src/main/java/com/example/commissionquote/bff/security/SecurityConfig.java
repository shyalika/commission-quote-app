package com.example.commissionquote.bff.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * One filter chain for every profile.
 *
 * <p>Every API endpoint needs a rule below with its required scope. Anything
 * without a rule is denied, so a new endpoint stays blocked until its rule is
 * added. Only the local profile has a way to authenticate (the dev token stub);
 * with no profile every API call gets 401, and prod refuses to start.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final String CREATE_QUOTES = "SCOPE_create:quotes";

    private final Environment environment;

    public SecurityConfig(Environment environment) {
        // Runs before the filter chain is built, so the stub can never exist under prod.
        ProdProfileStartupGuard.check(environment);
        this.environment = environment;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
        http
                // Bearer tokens, no cookies or sessions, so no CSRF risk. No CORS: the UI uses the same origin.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Any method, so a GET still reaches Spring MVC and gets 405; only POST is mapped.
                        .requestMatchers("/api/v1/quotes").hasAuthority(CREATE_QUOTES)
                        .anyRequest().denyAll())
                // 401 and 403 are built by ApiExceptionHandler, like every other error.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, e) ->
                                exceptionResolver.resolveException(request, response, null, e))
                        .accessDeniedHandler((request, response, e) ->
                                exceptionResolver.resolveException(request, response, null, e)));

        if (environment.matchesProfiles("local")) {
            http.addFilterBefore(new DevTokenAuthenticationFilter(), AnonymousAuthenticationFilter.class);
        }
        return http.build();
    }
}
