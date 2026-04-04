package com.nexus.notification.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket handler that supports targeted order update broadcasts.
 *
 * <p>Clients subscribe to specific orders by connecting with a query parameter:
 * {@code ws://host/ws/orders?orderId=<uuid>}. Broadcasts are delivered only
 * to sessions subscribed to the relevant orderId. Sessions without an orderId
 * parameter receive all broadcasts (backward-compatible).</p>
 */
@Component
@Slf4j
public class OrderStatusWebSocketHandler extends TextWebSocketHandler {

    /** orderId → subscribed sessions. Key "*" holds unfiltered (global) subscribers. */
    private final ConcurrentHashMap<String, Set<WebSocketSession>> subscriptions = new ConcurrentHashMap<>();

    private static final String GLOBAL_KEY = "*";

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String orderId = extractOrderId(session);
        String key = orderId != null ? orderId : GLOBAL_KEY;

        subscriptions.computeIfAbsent(key, k -> new CopyOnWriteArraySet<>()).add(session);
        log.info("WebSocket connected: sessionId={}, subscription={}", session.getId(), key);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        removeSession(session);
        log.info("WebSocket disconnected: sessionId={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error: sessionId={}, error={}", session.getId(), exception.getMessage());
        removeSession(session);
    }

    /**
     * Broadcasts an order status update to subscribed sessions only.
     *
     * <p>Delivers to sessions subscribed to the specific orderId plus
     * any global (unfiltered) subscribers.</p>
     */
    public void broadcastOrderUpdate(String orderId, String status, String message) {
        String payload = """
                {"orderId":"%s","status":"%s","message":"%s","timestamp":"%s"}"""
                .formatted(orderId, status, escapeJson(message), Instant.now());

        var textMessage = new TextMessage(payload);

        // Send to order-specific subscribers
        sendToGroup(orderId, textMessage);
        // Send to global subscribers (backward compatibility)
        sendToGroup(GLOBAL_KEY, textMessage);
    }

    private void sendToGroup(String key, TextMessage message) {
        Set<WebSocketSession> sessions = subscriptions.get(key);
        if (sessions == null) return;

        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                } else {
                    sessions.remove(session);
                }
            } catch (IOException e) {
                log.error("Failed to send WebSocket message: sessionId={}, error={}",
                        session.getId(), e.getMessage());
                sessions.remove(session);
            }
        }
    }

    private void removeSession(WebSocketSession session) {
        subscriptions.values().forEach(sessions -> sessions.remove(session));
    }

    private static String extractOrderId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) return null;

        for (String param : uri.getQuery().split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "orderId".equals(kv[0]) && !kv[1].isBlank()) {
                return kv[1];
            }
        }
        return null;
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
