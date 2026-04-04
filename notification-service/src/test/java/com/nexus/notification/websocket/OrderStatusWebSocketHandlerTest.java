package com.nexus.notification.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@DisplayName("OrderStatusWebSocketHandler")
class OrderStatusWebSocketHandlerTest {

    private OrderStatusWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OrderStatusWebSocketHandler();
    }

    private WebSocketSession globalSession(String id) {
        var session = mock(WebSocketSession.class);
        given(session.getId()).willReturn(id);
        // No orderId query param → global subscriber
        given(session.getUri()).willReturn(URI.create("ws://localhost/ws/orders"));
        return session;
    }

    private WebSocketSession orderSession(String id, String orderId) {
        var session = mock(WebSocketSession.class);
        given(session.getId()).willReturn(id);
        given(session.getUri()).willReturn(URI.create("ws://localhost/ws/orders?orderId=" + orderId));
        return session;
    }

    @Nested
    @DisplayName("Subscription filtering")
    class SubscriptionTests {

        @Test
        @DisplayName("global subscriber receives all broadcasts")
        void globalReceivesAll() throws Exception {
            var session = globalSession("global");
            given(session.isOpen()).willReturn(true);

            handler.afterConnectionEstablished(session);

            handler.broadcastOrderUpdate("order-A", "CONFIRMED", "done");
            handler.broadcastOrderUpdate("order-B", "CANCELLED", "failed");

            verify(session, times(2)).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("order-specific subscriber receives only matching broadcasts")
        void filteredReceivesOnlyMatching() throws Exception {
            var subscribedSession = orderSession("s1", "order-A");
            given(subscribedSession.isOpen()).willReturn(true);

            handler.afterConnectionEstablished(subscribedSession);

            handler.broadcastOrderUpdate("order-A", "CONFIRMED", "done");
            handler.broadcastOrderUpdate("order-B", "CANCELLED", "failed");

            // Only one message — the order-A broadcast
            verify(subscribedSession, times(1)).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("multiple subscribers to different orders only get their own updates")
        void multipleOrders() throws Exception {
            var sessionA = orderSession("sA", "order-A");
            var sessionB = orderSession("sB", "order-B");
            given(sessionA.isOpen()).willReturn(true);
            given(sessionB.isOpen()).willReturn(true);

            handler.afterConnectionEstablished(sessionA);
            handler.afterConnectionEstablished(sessionB);

            handler.broadcastOrderUpdate("order-A", "CONFIRMED", "done");

            verify(sessionA, times(1)).sendMessage(any(TextMessage.class));
            verify(sessionB, never()).sendMessage(any(TextMessage.class));
        }
    }

    @Nested
    @DisplayName("Connection lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("closed sessions are removed from broadcast list")
        void removesOnClose() throws Exception {
            var session = globalSession("to-remove");
            given(session.isOpen()).willReturn(true);

            handler.afterConnectionEstablished(session);
            handler.afterConnectionClosed(session, CloseStatus.NORMAL);

            handler.broadcastOrderUpdate("order-1", "CONFIRMED", "done");

            verify(session, never()).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("stale sessions detected during broadcast are removed")
        void removesStale() throws Exception {
            var open = globalSession("open");
            var stale = globalSession("stale");
            given(open.isOpen()).willReturn(true);
            given(stale.isOpen()).willReturn(false);

            handler.afterConnectionEstablished(open);
            handler.afterConnectionEstablished(stale);

            handler.broadcastOrderUpdate("order-1", "CONFIRMED", "done");

            verify(open).sendMessage(any(TextMessage.class));
            verify(stale, never()).sendMessage(any(TextMessage.class));
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorTests {

        @Test
        @DisplayName("IOException on one session does not affect others")
        void isolatesFailure() throws Exception {
            var failing = globalSession("failing");
            var healthy = globalSession("healthy");
            given(failing.isOpen()).willReturn(true);
            given(healthy.isOpen()).willReturn(true);
            doThrow(new IOException("broken pipe")).when(failing).sendMessage(any(TextMessage.class));

            handler.afterConnectionEstablished(failing);
            handler.afterConnectionEstablished(healthy);

            handler.broadcastOrderUpdate("order-1", "CANCELLED", "err");

            verify(healthy).sendMessage(any(TextMessage.class));
        }

        @Test
        @DisplayName("transport error removes session")
        void transportErrorRemoves() throws Exception {
            var session = globalSession("error-session");
            given(session.isOpen()).willReturn(true);

            handler.afterConnectionEstablished(session);
            handler.handleTransportError(session, new RuntimeException("timeout"));

            handler.broadcastOrderUpdate("order-1", "CONFIRMED", "done");

            verify(session, never()).sendMessage(any(TextMessage.class));
        }
    }
}
