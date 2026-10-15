package dev.resqnet.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing.
 *
 * <p>Kept in its own configuration class rather than beside the filter chain.
 * {@code SecurityConfig} depends on the authentication filter, which depends on
 * {@code AuthTokenService}, which needs a {@code PasswordEncoder}. Defining the
 * encoder here breaks that cycle without resorting to lazy injection, which
 * would hide the problem rather than solve it.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Cost 12 is materially slower to brute-force than the default 10 and
        // turns a leaked hash table into a real expense for an attacker.
        return new BCryptPasswordEncoder(12);
    }
}
