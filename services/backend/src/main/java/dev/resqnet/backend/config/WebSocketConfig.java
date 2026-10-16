package dev.resqnet.backend.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import dev.resqnet.backend.security.WebSocketAuthInterceptor;
import dev.resqnet.backend.web.EventStreamHandler;

/**
 * Registers the dashboard event stream.
 *
 * <p>Origins are restricted to the same list as the API. A dashboard served
 * from an unexpected origin cannot open the socket, which closes a cross-site
 * WebSocket hijack that a cookie-authenticated deployment would otherwise be
 * open to.
 *
 * <p>That claim was previously made in this file's javadoc and contradicted by
 * the code, which passed {@code "*"}: the REST allow-list is registered for
 * {@code /api/**} and never applies to {@code /ws/incidents}, so any page
 * anyone could get a responder to visit could subscribe to the incident stream.
 * With no authentication on the upgrade at all, that was an open feed.
 *
 * <p>The upgrade is authenticated by {@link WebSocketAuthInterceptor} because a
 * browser cannot set headers on a WebSocket.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final EventStreamHandler handler;
    private final WebSocketAuthInterceptor authInterceptor;
    private final List<String> allowedOrigins;

    public WebSocketConfig(EventStreamHandler handler, WebSocketAuthInterceptor authInterceptor,
                           @Value("${resqnet.security.allowed-origins:http://localhost:5173}")
                           List<String> allowedOrigins) {
        this.handler = handler;
        this.authInterceptor = authInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/incidents")
                .addInterceptors(authInterceptor)
                .setAllowedOrigins(allowedOrigins.toArray(new String[0]));
    }
}