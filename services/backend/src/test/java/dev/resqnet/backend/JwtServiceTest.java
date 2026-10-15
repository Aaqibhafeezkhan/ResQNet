package dev.resqnet.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;

import dev.resqnet.backend.domain.ResponderRole;
import dev.resqnet.backend.security.JwtService;

/**
 * Token issuance and verification.
 *
 * <p>These tests exist because the failure modes are quiet. A verifier that
 * accepts a tampered payload, or an expired token, or a token signed with a
 * different algorithm, produces a system that looks secure and is not.
 */
@TestPropertySource(properties = {
        "resqnet.security.jwt-secret=resqnet-test-secret-that-is-definitely-long-enough-32",
        "spring.profiles.active=test" })
class JwtServiceTest {

    private JwtService service() {
        return new JwtService(
                "resqnet-test-secret-that-is-definitely-long-enough-32", 30, "test");
    }

    @Test
    @DisplayName("round-trips a token with the claims it was issued with")
    void roundTrips() {
        String token = service().issue("responder", ResponderRole.RESPONDER, 7L);

        JwtService.Claims claims = service().verify(token).orElseThrow();

        assertThat(claims.subject()).isEqualTo("responder");
        assertThat(claims.role()).isEqualTo(ResponderRole.RESPONDER);
        assertThat(claims.accountId()).isEqualTo(7L);
        assertThat(claims.expired()).isFalse();
    }

    @Test
    @DisplayName("rejects a token whose payload was edited to grant admin")
    void rejectsTamperedPayload() {
        String token = service().issue("responder", ResponderRole.RESPONDER, 7L);
        String[] parts = token.split("\\.");

        // Same signature, different claim: exactly the attack this closes.
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"responder\",\"role\":\"ADMIN\",\"aid\":7,"
                        .concat("\"exp\":9999999999}").getBytes());
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThat(service().verify(forged)).isEmpty();
    }

    @Test
    @DisplayName("rejects a token signed with a different secret")
    void rejectsForeignSignature() {
        String foreign = new JwtService("a-completely-different-secret-of-sufficient-length",
                30, "test").issue("responder", ResponderRole.ADMIN, 1L);

        assertThat(service().verify(foreign)).isEmpty();
    }

    @Test
    @DisplayName("rejects a token that declares a different algorithm")
    void rejectsAlgorithmSwap() {
        String header = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
        String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"admin\",\"role\":\"ADMIN\",\"aid\":1,\"exp\":"
                        + (java.time.Instant.now().getEpochSecond() + 3600) + "}").getBytes());

        assertThat(service().verify(header + "." + payload + ".")).isEmpty();
    }

    @Test
    @DisplayName("rejects an expired token rather than handing back its claims")
    void rejectsExpired() throws Exception {
        // A token whose exp claim is already in the past. Handing the claims back
        // and relying on every caller to remember would make revocation depend on
        // perfect memory at every call site.
        String header = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes());
        String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"responder\",\"role\":\"ADMIN\",\"aid\":1,"
                        + "\"exp\":" + (java.time.Instant.now().getEpochSecond() - 60) + "}")
                        .getBytes());
        JwtService service = service();
        String signingInput = header + "." + payload;
        String forged = signingInput + "." + signatureOf(signingInput,
                "resqnet-test-secret-that-is-definitely-long-enough-32");

        assertThat(service.verify(forged)).isEmpty();
    }

    @Test
    @DisplayName("accepts a token that has not expired yet")
    void acceptsLiveToken() {
        String token = service().issue("responder", ResponderRole.RESPONDER, 1L);

        assertThat(service().verify(token).orElseThrow().expired()).isFalse();
    }

    /** Recomputes an HMAC signature so a test can build a token with any expiry. */
    private static String signatureOf(String input, String secret) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(input.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("rejects malformed tokens without leaking why")
    void rejectsMalformed() {
        JwtService service = service();

        assertThat(service.verify(null)).isEmpty();
        assertThat(service.verify("")).isEmpty();
        assertThat(service.verify("not-a-token")).isEmpty();
        assertThat(service.verify("a.b")).isEmpty();
        assertThat(service.verify("a.b.c.d")).isEmpty();
    }

    @Test
    @DisplayName("refuses to start without a secret outside development")
    void refusesMissingSecret() {
        assertThatThrownBy(() -> new JwtService("", 60, "production"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt-secret");
    }

    @Test
    @DisplayName("refuses a secret that is too short to be meaningful")
    void refusesShortSecret() {
        assertThatThrownBy(() -> new JwtService("short", 60, "production"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least");
    }

    @Test
    @DisplayName("parses its own payload shape correctly")
    void parsesPayload() {
        java.util.Map<String, Object> parsed = JwtService.miniJson(
                "{\"sub\":\"a\",\"role\":\"ADMIN\",\"aid\":12,\"exp\":1700000000}");

        assertThat(parsed).containsEntry("sub", "a")
                .containsEntry("role", "ADMIN")
                .containsEntry("exp", 1700000000.0);
        assertThat((Double) parsed.get("aid")).isEqualTo(12.0);
    }
}
