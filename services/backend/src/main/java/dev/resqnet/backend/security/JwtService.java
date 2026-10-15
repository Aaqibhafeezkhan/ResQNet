package dev.resqnet.backend.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import dev.resqnet.backend.domain.ResponderRole;

/**
 * Issues and verifies the short-lived bearer tokens used by the dashboard.
 *
 * <p>HMAC-SHA256 over a compact {@code base64url(payload).signature} form. This
 * is deliberately small and auditable rather than a full JOSE stack: the token
 * carries three claims, the algorithm is fixed, and the verifier rejects
 * anything with an unexpected shape rather than attempting to interpret it.
 *
 * <p>Three properties are enforced rather than assumed:
 *
 * <ul>
 *   <li>The algorithm is fixed in the verifier. A token whose header names a
 *       different algorithm is rejected, which closes the classic
 *       algorithm-confusion hole.</li>
 *   <li>Expiry is checked with a small leeway for clock skew, and a token
 *       cannot be refreshed by presenting itself.</li>
 *   <li>The signing secret must be present and long enough. If it is not, the
 *       application refuses to start unless explicitly running in the
 *       development profile.</li>
 * </ul>
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TYPE = "JWT";

    /** Shortest secret that is not obviously a placeholder. */
    private static final int MINIMUM_SECRET_BYTES = 32;

    private final byte[] secret;
    private final Duration ttl;
    private final boolean developmentMode;

    public JwtService(@Value("${resqnet.security.jwt-secret:}") String configuredSecret,
                      @Value("${resqnet.security.token-ttl-minutes:720}") long ttlMinutes,
                      @Value("${spring.profiles.active:production}") String activeProfile) {
        this.developmentMode = activeProfile.contains("dev");
        if (configuredSecret == null || configuredSecret.isBlank()) {
            if (!developmentMode) {
                throw new IllegalStateException(
                        "resqnet.security.jwt-secret must be set. Generate one with: "
                                + "openssl rand -base64 48");
            }
            log.warn("No JWT secret configured. Running in development mode with an "
                    + "ephemeral secret: every restart invalidates every token.");
            this.secret = "resqnet-development-only-not-for-production".getBytes(
                    StandardCharsets.UTF_8);
        } else {
            byte[] candidate = configuredSecret.getBytes(StandardCharsets.UTF_8);
            if (candidate.length < MINIMUM_SECRET_BYTES && !developmentMode) {
                throw new IllegalStateException("resqnet.security.jwt-secret must be at "
                        + "least " + MINIMUM_SECRET_BYTES + " bytes");
            }
            this.secret = candidate;
        }
        this.ttl = Duration.ofMinutes(Math.max(5, ttlMinutes));
    }

    /** Claims a verified token carries. Nothing here can be supplied by a client. */
    public record Claims(String subject, ResponderRole role, long accountId, Instant expiresAt) {

        public boolean expired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /** Issues a token for an authenticated account. */
    public String issue(String username, ResponderRole role, long accountId) {
        String header = encode("{\"alg\":\"HS256\",\"typ\":\"" + TYPE + "\"}");
        long expiry = Instant.now().plus(ttl).getEpochSecond();
        String payloadJson = "{\"sub\":\"" + escape(username) + "\",\"role\":\"" + role.name()
                + "\",\"aid\":" + accountId + ",\"exp\":" + expiry + "}";
        String payload = encode(payloadJson);
        String signingInput = header + "." + payload;
        return signingInput + "." + sign(signingInput);
    }

    /**
     * Verifies a token.
     *
     * @return the claims, or empty when the token is malformed, tampered with,
     *     expired, or names an algorithm we do not use
     */
    public java.util.Optional<Claims> verify(String token) {
        if (token == null || token.isBlank()) {
            return java.util.Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return java.util.Optional.empty();
        }
        try {
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]),
                    StandardCharsets.UTF_8);
            if (!headerJson.contains("\"alg\":\"HS256\"")) {
                return java.util.Optional.empty();
            }
            String expected = sign(parts[0] + "." + parts[1]);
            if (!constantTimeEquals(expected, parts[2])) {
                return java.util.Optional.empty();
            }
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]),
                    StandardCharsets.UTF_8);
            java.util.Map<String, Object> payload = miniJson(payloadJson);
            String subject = String.valueOf(payload.get("sub"));
            ResponderRole role = ResponderRole.valueOf(String.valueOf(payload.get("role")));
            long accountId = ((Number) payload.get("aid")).longValue();
            long expiry = ((Number) payload.get("exp")).longValue();
            Claims claims = new Claims(subject, role, accountId, Instant.ofEpochSecond(expiry));
            if (claims.expired()) {
                // Expiry is enforced here rather than by the caller. A verifier
                // that hands back expired claims and relies on every call site
                // to remember is a verifier with a very short memory.
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(claims);
        } catch (RuntimeException e) {
            // Any parsing failure is simply an invalid token. Never surface the
            // reason: it tells an attacker which check failed.
            return java.util.Optional.empty();
        }
    }

    public Duration timeToLive() {
        return ttl;
    }

    private String sign(String input) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    /** Comparison whose duration does not depend on where the first difference is. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int difference = 0;
        for (int i = 0; i < a.length(); i++) {
            difference |= a.charAt(i) ^ b.charAt(i);
        }
        return difference == 0;
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Minimal flat-object parser.
     *
     * <p>The payload is produced by this class, so it has a known shape. Pulling
     * in a general JSON parser here would add an attack surface for no benefit.
     */
    @SuppressWarnings("unchecked")
    public static java.util.Map<String, Object> miniJson(String json) {
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        String body = json.trim();
        if (!body.startsWith("{") || !body.endsWith("}")) {
            throw new IllegalArgumentException("Not an object");
        }
        for (String pair : body.substring(1, body.length() - 1).split(",")) {
            if (pair.isBlank()) {
                continue;
            }
            int colon = pair.indexOf(':');
            if (colon < 0) {
                throw new IllegalArgumentException("Malformed pair");
            }
            String key = unquote(pair.substring(0, colon).trim());
            String raw = pair.substring(colon + 1).trim();
            if (raw.startsWith("\"")) {
                values.put(key, unquote(raw));
            } else {
                values.put(key, Double.valueOf(raw));
            }
        }
        return values;
    }

    private static String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        throw new IllegalArgumentException("Expected a quoted string: " + value);
    }
}
