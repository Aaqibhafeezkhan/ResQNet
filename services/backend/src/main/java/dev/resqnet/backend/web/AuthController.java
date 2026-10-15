package dev.resqnet.backend.web;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.core.Authentication;

import dev.resqnet.backend.security.AuthTokenService;
import dev.resqnet.backend.security.ResQNetPrincipal;

/**
 * Sign-in for the responder dashboard.
 *
 * <p>No registration endpoint exists by design. Adding one would let anyone who
 * can reach the endpoint create an account, and "they signed themselves up" is
 * not an acceptable answer to "who is allowed to see emergency reports".
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthTokenService authTokens;

    public AuthController(AuthTokenService authTokens) {
        this.authTokens = authTokens;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody Dtos.LoginRequest request) {
        AuthTokenService.AuthResult result = authTokens.authenticateResponder(
                request.username(), request.password());
        if (!result.success()) {
            // One message for every failure mode. Distinguishing them would tell
            // an attacker which usernames are real.
            return ResponseEntity.status(401)
                    .body(Map.of("error", "invalid credentials"));
        }
        var principal = result.principal();
        return ResponseEntity.ok(new Dtos.LoginResponse(
                result.token(),
                principal.name(),
                principal.role().name(),
                43_200L));
    }

    /** Current identity, used by the dashboard to render role-aware controls. */
    @PostMapping("/whoami")
    public ResponseEntity<?> whoami(Authentication auth) {
        if (!(auth != null && auth.getPrincipal() instanceof ResQNetPrincipal principal)) {
            return ResponseEntity.status(401).body(Map.of("error", "not authenticated"));
        }
        return ResponseEntity.ok(Map.of(
                "name", principal.name(),
                "role", principal.role().name(),
                "canMutateIncidents", principal.canMutateIncidents(),
                "canReadAudit", principal.canReadAudit()));
    }
}
