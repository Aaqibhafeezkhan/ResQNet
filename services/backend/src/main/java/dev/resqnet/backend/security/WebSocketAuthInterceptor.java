package dev.resqnet.backend.security;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Authenticates a dashboard event stream at the HTTP upgrade.
 *
 * <h2>Why not the Authorization header</h2>
 *
 * <p>A browser cannot set headers on a WebSocket. The constructor takes no
 * options at all, so the usual {@code Authorization: Bearer} that protects every
 * other endpoint is simply unavailable here. That leaves two places to carry a
 * credential: the query string or {@code Sec-WebSocket-Protocol}.
 *
 * <h2>Why the subprotocol and not the query string</h2>
 *
 * <p>A token in the query string lands in access logs, proxy logs and browser
 * history. The subprotocol header is sent as part of the upgrade request and
 * appears in none of those. It is a slightly odd use of the field, which is why
 * the client and this interceptor agree on the exact shape:
 *
 * <pre>
 *   new WebSocket(url, ['resqnet.v1', 'bearer.' + token])
 * </pre>
 *
 * <p>The token is a responder JWT, so the socket carries exactly the authority
 * its holder already has. Both RESPONDER and ADMIN may subscribe, because
 * watching the queue change is not an authority question: a responder who can
 * triage an incident plainly needs to see it appear. A device token is
 * rejected, because a gateway may report and this stream would make it a
 * reader.
 *
 * <p>Failure is a 401 at the upgrade, which a browser surfaces as a plain
 * connection error. The server also emits an {@code unauthorized} close frame
 * where the handshake allows it, so a client that reached this far can tell
 * "no permission" from "network is down".
 */
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    /** Subprotocol token identifying this protocol version. */
    public static final String PROTOCOL = "resqnet.v1";

    /** Prefix for the credential subprotocol. */
    public static final String CREDENTIAL_PREFIX = "bearer.";

    /** Attribute the resolved principal is stored under. */
    public static final String PRINCIPAL_ATTRIBUTE = "resqnet.principal";

    private final AuthTokenService authTokens;

    public WebSocketAuthInterceptor(AuthTokenService authTokens) {
        this.authTokens = authTokens;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String credential = extract(request);
        if (credential == null) {
            log.debug("WebSocket upgrade refused: no credential subprotocol");
            return false;
        }
        var principal = authTokens.resolveBearer(credential);
        if (principal.isEmpty()) {
            log.warn("WebSocket upgrade refused: unusable or expired token");
            return false;
        }
        if (!principal.get().isResponder()) {
            // A device token is rejected here. A gateway may report; it has no
            // business watching every incident in the system change in real
            // time, and giving it that view would turn a reporting credential
            // into a read credential.
            log.warn("WebSocket upgrade refused: {} is not a responder session",
                    principal.get().name());
            return false;
        }
        attributes.put(PRINCIPAL_ATTRIBUTE, principal.get().name());
        attributes.put("resqnet.role", principal.get().role() == null
                ? "NONE" : principal.get().role().name());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Nothing to clean up. The session list is maintained by the handler.
    }

    /**
     * Pulls the credential out of the {@code Sec-WebSocket-Protocol} header.
     *
     * <p>A query parameter is accepted as a fallback because some test clients
     * and command line tools cannot set the header. It is documented on the
     * client as less safe for exactly that reason.
     */
    static String extract(ServerHttpRequest request) {
        List<String> offered = request.getHeaders().get("Sec-WebSocket-Protocol");
        if (offered != null) {
            for (String header : offered) {
                for (String candidate : header.split(",")) {
                    String trimmed = candidate.trim();
                    if (trimmed.startsWith(CREDENTIAL_PREFIX)) {
                        return trimmed.substring(CREDENTIAL_PREFIX.length()).trim();
                    }
                }
            }
        }
        if (request instanceof ServletServerHttpRequest servlet) {
            HttpServletRequest raw = servlet.getServletRequest();
            String parameter = raw.getParameter("token");
            if (parameter != null && !parameter.isBlank()) {
                return parameter.trim();
            }
        }
        return null;
    }
}