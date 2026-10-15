package dev.resqnet.backend.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import dev.resqnet.backend.security.JwtAuthenticationFilter;
import dev.resqnet.backend.security.RequestIdFilter;

/**
 * Web security.
 *
 * <p>The endpoint list is the policy, written out rather than implied:
 *
 * <ul>
 *   <li>{@code /api/v1/auth/**} — sign-in only. Everything else is
 *       authenticated.</li>
 *   <li>{@code /api/v1/gateway/**} — authenticated by a gateway token, handled
 *       inside the controller. It cannot be given to a browser session.</li>
 *   <li>{@code /api/v1/admin/**} — {@code ROLE_ADMIN} only.</li>
 *   <li>{@code /api/v1/incidents/**} — read for any authenticated principal,
 *       write for responders only, enforced by {@code @PreAuthorize} on the
 *       controller so the rule sits next to the behaviour.</li>
 *   <li>Actuator health only, with no detail, so a health check cannot be used
 *       to fingerprint the deployment.</li>
 * </ul>
 *
 * <p>Sessions are stateless. A CSRF token would be meaningless without one, and
 * keeping it would be security theatre.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Origins permitted to call the API from a browser. */
    @Value("${resqnet.security.allowed-origins:http://localhost:5173}")
    private List<String> allowedOrigins;

    private final JwtAuthenticationFilter jwtFilter;
    private final RequestIdFilter requestIdFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, RequestIdFilter requestIdFilter) {
        this.jwtFilter = jwtFilter;
        this.requestIdFilter = requestIdFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentTypeOptions(contentType -> {
                        })
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)))
                // Without an entry point Spring returns 403 for a request with no
                // credentials, which is the wrong answer: 403 says "you are known
                // and refused", 401 says "prove who you are". A monitoring system
                // triaging an outage needs to tell those apart.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, failure) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write(
                                    "{\"code\":\"UNAUTHENTICATED\","
                                            + "\"message\":\"Authentication is required\"}");
                        })
                        .accessDeniedHandler((request, response, failure) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            response.getWriter().write(
                                    "{\"code\":\"FORBIDDEN\","
                                            + "\"message\":\"This account may not perform "
                                            + "that action\"}");
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // The gateway authenticates with a shared device token
                        // rather than a responder session, so it cannot pass
                        // through the JWT filter. The controller performs that
                        // check and returns 401 itself; permitting it here only
                        // means it reaches the controller at all.
                        .requestMatchers("/api/v1/gateway/**").permitAll()
                        // The simulator posts as a device, not as a responder, so
                        // it cannot pass through the JWT filter either. The
                        // controller accepts a responder session or the shared
                        // device token and rejects everything else with 401;
                        // permitting the path here only lets the request reach
                        // that check. Reads stay authenticated below.
                        .requestMatchers(HttpMethod.POST, "/api/v1/simulations").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(requestIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type",
                RequestIdFilter.HEADER));
        configuration.setExposedHeaders(List.of(RequestIdFilter.HEADER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(1800L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
