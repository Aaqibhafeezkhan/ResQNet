package dev.resqnet.backend.web;

import jakarta.validation.Valid;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.resqnet.backend.security.AuthTokenService;
import dev.resqnet.backend.security.RequestIdFilter;
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
                                  jakarta.servlet.http.HttpServletRequest servletRequest) {
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7).trim() : "";
        var principal = authTokens.authenticateGateway(token, request.deviceId());
        if (principal.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(java.util.Map.of("error", "gateway authentication failed"));
        }
        // From the request attribute, not the SLF4J MDC. The MDC key is never
        // written anywhere, so every gateway-originated audit row was stamped
        // with the literal string "gateway-sync". That defeats the point of
        // carrying a correlation id: after an incident you cannot tie a specific
        // sync back to the logs of that request, only to every other sync.
        String requestId = (String) servletRequest.getAttribute(RequestIdFilter.HEADER);
        return ResponseEntity.ok(sync.ingest(request,
                requestId == null ? "gateway-sync" : requestId));
    }
}
