package dev.resqnet.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import dev.resqnet.backend.web.EventStreamHandler;

/**
 * Registers the dashboard event stream.
 *
 * <p>Allowed origins are the same list as the API. A dashboard served from an
 * unexpected origin cannot open the socket, which closes a cross-site
 * WebSocket hijack that a cookie-authenticated deployment would otherwise be
 * open to.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final EventStreamHandler handler;

    public WebSocketConfig(EventStreamHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/incidents")
                .setAllowedOriginPatterns("*");
    }
}
