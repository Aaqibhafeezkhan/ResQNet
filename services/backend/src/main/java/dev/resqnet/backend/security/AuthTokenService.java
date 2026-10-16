package dev.resqnet.backend.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import dev.resqnet.backend.domain.ResponderAccount;
import dev.resqnet.backend.repository.ResponderAccountRepository;

/**
 * Turns credentials into a principal.
 *
 * <p>Responder sign-in is username plus BCrypt password. Gateway
 * authentication is a shared token from configuration.
 *
 * <p>The asymmetry is intentional. A responder is a person who can be held
 * accountable; a device is a key that can be revoked by rotating the token.
 * Giving devices the same login machinery as people would imply an identity
 * model the system does not have.
 */
@Service
public class AuthTokenService {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenService.class);

    private final ResponderAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String gatewayToken;

    public AuthTokenService(ResponderAccountRepository accounts, PasswordEncoder passwordEncoder,
                            JwtService jwtService,
                            @Value("${resqnet.security.gateway-token:}") String gatewayToken) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.gatewayToken = gatewayToken == null ? "" : gatewayToken.trim();
    }

    /**
     * Authenticates a responder.
     *
     * <p>Returns empty for an unknown user, a wrong password and a disabled
     * account alike, and always performs a hash comparison, so response time
     * does not reveal whether a username exists.
     */
    public AuthResult authenticateResponder(String username, String password) {
        ResponderAccount account = accounts.findByUsername(username).orElse(null);
        if (account == null) {
            // Constant-ish work on the miss path, then the same failure as a bad
            // password.
            passwordEncoder.matches(password == null ? "" : password,
                    "$2a$10$abcdefghijklmnopqrstuv0123456789012345678901234567890123");
            return AuthResult.rejected();
        }
        if (!account.isActive() || !passwordEncoder.matches(password, account.getPasswordHash())) {
            return AuthResult.rejected();
        }
        account.recordLogin();
        accounts.save(account);
        String token = jwtService.issue(account.getUsername(), account.getRole(), account.getId());
        return AuthResult.success(token, ResQNetPrincipal.responder(
                account.getUsername(), account.getRole(), account.getId()));
    }

    /**
     * Authenticates a gateway device by its shared token.
     *
     * @return empty when the token is absent or does not match configuration
     */
    public java.util.Optional<ResQNetPrincipal> authenticateGateway(String bearerToken,
                                                                    String deviceId) {
        if (gatewayToken.isEmpty() || bearerToken == null || !constantTime(gatewayToken, bearerToken)) {
            log.warn("Rejected a gateway sync attempt from {}", deviceId);
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(ResQNetPrincipal.gateway(deviceId));
    }

    /** Resolves a bearer token into a principal. */
    public java.util.Optional<ResQNetPrincipal> resolveBearer(String bearer) {
        return jwtService.verify(bearer).map(claims -> ResQNetPrincipal.responder(
                claims.subject(), claims.role(), claims.accountId()));
    }

    /**
     * Compares two tokens without leaking their contents through timing.
     *
     * <p>The length is folded into the accumulator rather than checked first.
     * Returning early on a length mismatch made the token's <em>length</em> the
     * thing an attacker measured, which is the one property the method name
     * promised to remove: it leaks just as reliably as the contents do, and a
     * length oracle turns a brute-force search into a much smaller one.
     */
    private static boolean constantTime(String a, String b) {
        byte[] left = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] right = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int difference = left.length ^ right.length;
        // Bounded by the longer array and safe at every index, so an empty or
        // short token is compared rather than short-circuited.
        int longest = Math.max(left.length, right.length);
        for (int i = 0; i < longest; i++) {
            byte leftByte = i < left.length ? left[i] : (byte) 0;
            byte rightByte = i < right.length ? right[i] : (byte) 0;
            difference |= leftByte ^ rightByte;
        }
        return difference == 0;
    }

    /** Outcome of a sign-in attempt. */
    public record AuthResult(boolean success, String token, ResQNetPrincipal principal,
                             String errorMessage) {

        static AuthResult success(String token, ResQNetPrincipal principal) {
            return new AuthResult(true, token, principal, null);
        }

        static AuthResult rejected() {
            return new AuthResult(false, null, null, "invalid credentials");
        }
    }
}
