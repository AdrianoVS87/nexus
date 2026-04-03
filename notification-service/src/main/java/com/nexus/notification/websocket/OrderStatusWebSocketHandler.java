package com.nexus.notification.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
public class OrderStatusWebSocketHandler extends TextWebSocketHandler {

    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket connected: sessionId={}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket disconnected: sessionId={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error: sessionId={}, error={}", session.getId(), exception.getMessage());
        sessions.remove(session);
    }

    public void broadcastOrderUpdate(String orderId, String status, String message) {
        String payload = """
                {"orderId":"%s","status":"%s","message":"%s","timestamp":"%s"}"""
                .formatted(orderId, status, message, Instant.now());

        sessions.forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(payload));
                } else {
                    sessions.remove(session);
                }
            } catch (IOException e) {
                log.error("Failed to send WebSocket message: sessionId={}, error={}", session.getId(), e.getMessage());
                sessions.remove(session);
            }
        });
    }
}
