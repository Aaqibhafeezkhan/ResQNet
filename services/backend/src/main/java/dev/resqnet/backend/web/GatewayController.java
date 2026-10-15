package dev.resqnet.backend.web;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.resqnet.backend.security.AuthTokenService;
import dev.resqnet.backend.service.GatewaySyncService;

/**
 * Synchronisation endpoint for a handset that has connectivity.
 *
 * <p>Authenticated by a shared gateway token rather than a responder token.
 * A device cannot obtain a responder session, and a responder token cannot be
 * used to push device data. The two directions are separate on purpose.
 */
@RestController
@RequestMapping("/api/v1/gateway")
public class GatewayController {

    private final GatewaySyncService sync;
    private final AuthTokenService authTokens;

    public GatewayController(GatewaySyncService sync, AuthTokenService authTokens) {
        this.sync = sync;
        this.authTokens = authTokens;
    }

    @PostMapping("/sync")
    public ResponseEntity<?> sync(@Valid @RequestBody Dtos.GatewaySyncRequest request,
                                  @RequestHeader(value = "Authorization", required = false)
                                  String authorization,
                                  @RequestHeader(value = "X-ResQNet-Source", required = false)
                                  String source) {
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7).trim() : "";
        var principal = authTokens.authenticateGateway(token, request.deviceId());
        if (principal.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(java.util.Map.of("error", "gateway authentication failed"));
        }
        String requestId = org.slf4j.MDC.get("requestId");
        return ResponseEntity.ok(sync.ingest(request,
                requestId == null ? "gateway-sync" : requestId));
    }
}
