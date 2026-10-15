package dev.resqnet.backend.web;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Pushes live incident changes to connected dashboards.
 *
 * <p>Plain WebSocket with bearer authentication performed at the HTTP upgrade
 * rather than a STOMP broker. The traffic is a handful of small JSON frames on
 * a network that a responder is already using for everything else; the simpler
 * transport is one fewer thing to configure wrongly.
 *
 * <p>The handler is deliberately defensive. A slow or dying client must never
 * block the ingestion path, so sends are best-effort and a failure closes that
 * one session instead of propagating.
 */
@Component
public class EventStreamHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(EventStreamHandler.class);

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        send(session, "{\"type\":\"connected\",\"at\":\"" + java.time.Instant.now() + "\"}");
        log.debug("Dashboard connected: {} ({} open)", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.debug("Dashboard disconnected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // The only inbound message is a keepalive. Nothing a client sends can
        // change server state.
        if (message.getPayload().contains("ping")) {
            try {
                session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
            } catch (IOException e) {
                closeQuietly(session);
            }
        }
    }

    /** Broadcasts an event to every connected dashboard. */
    public void broadcast(String json) {
        if (sessions.isEmpty()) {
            return;
        }
        for (WebSocketSession session : sessions) {
            send(session, json);
        }
    }

    private void send(WebSocketSession session, String json) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException | IllegalStateException e) {
            // A dead dashboard is not an ingestion failure.
            closeQuietly(session);
        }
    }

    private void closeQuietly(WebSocketSession session) {
        sessions.remove(session);
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException ignored) {
            // Already gone. Nothing useful to do.
        }
    }

    /** Number of dashboards currently connected. */
    public int connectedDashboards() {
        return sessions.size();
    }
}
