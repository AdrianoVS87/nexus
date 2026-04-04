package com.nexus.notification.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class OrderStatusWebSocketHandlerTest {

    private OrderStatusWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OrderStatusWebSocketHandler();
    }

    @Test
    @DisplayName("broadcastOrderUpdate sends message to all open sessions")
    void broadcastOrderUpdate_sendsToAllOpenSessions() throws Exception {
        var session1 = mock(WebSocketSession.class);
        var session2 = mock(WebSocketSession.class);
        given(session1.isOpen()).willReturn(true);
        given(session1.getId()).willReturn("s1");
        given(session2.isOpen()).willReturn(true);
        given(session2.getId()).willReturn("s2");

        handler.afterConnectionEstablished(session1);
        handler.afterConnectionEstablished(session2);

        handler.broadcastOrderUpdate("order-1", "CONFIRMED", "Your order is confirmed");

        verify(session1).sendMessage(any(TextMessage.class));
        verify(session2).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("broadcastOrderUpdate removes closed sessions during broadcast")
    void broadcastOrderUpdate_removesClosedSessions() throws Exception {
        var openSession = mock(WebSocketSession.class);
        var closedSession = mock(WebSocketSession.class);
        given(openSession.isOpen()).willReturn(true);
        given(openSession.getId()).willReturn("open");
        given(closedSession.isOpen()).willReturn(false);
        given(closedSession.getId()).willReturn("closed");

        handler.afterConnectionEstablished(openSession);
        handler.afterConnectionEstablished(closedSession);

        handler.broadcastOrderUpdate("order-1", "CONFIRMED", "Confirmed");

        verify(openSession).sendMessage(any(TextMessage.class));
        verify(closedSession, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("broadcastOrderUpdate handles IOException per session without crashing")
    void broadcastOrderUpdate_handlesIOExceptionPerSession() throws Exception {
        var failingSession = mock(WebSocketSession.class);
        var healthySession = mock(WebSocketSession.class);
        given(failingSession.isOpen()).willReturn(true);
        given(failingSession.getId()).willReturn("failing");
        given(healthySession.isOpen()).willReturn(true);
        given(healthySession.getId()).willReturn("healthy");
        doThrow(new IOException("broken pipe")).when(failingSession).sendMessage(any(TextMessage.class));

        handler.afterConnectionEstablished(failingSession);
        handler.afterConnectionEstablished(healthySession);

        handler.broadcastOrderUpdate("order-1", "CANCELLED", "Cancelled");

        verify(healthySession).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("afterConnectionEstablished adds session to broadcast list")
    void afterConnectionEstablished_addsSessions() throws Exception {
        var session = mock(WebSocketSession.class);
        given(session.isOpen()).willReturn(true);
        given(session.getId()).willReturn("new-session");

        handler.afterConnectionEstablished(session);
        handler.broadcastOrderUpdate("order-1", "STATUS", "msg");

        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("afterConnectionClosed removes session from broadcast list")
    void afterConnectionClosed_removesSession() throws Exception {
        var session = mock(WebSocketSession.class);
        given(session.getId()).willReturn("to-remove");

        handler.afterConnectionEstablished(session);
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        handler.broadcastOrderUpdate("order-1", "STATUS", "msg");

        verify(session, never()).sendMessage(any(TextMessage.class));
    }
}
