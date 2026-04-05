package com.nexus.notification.service;

import com.nexus.common.event.OrderCancelled;
import com.nexus.common.event.OrderConfirmed;
import com.nexus.common.event.PaymentFailed;
import com.nexus.notification.websocket.OrderStatusWebSocketHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    @Mock
    private OrderStatusWebSocketHandler webSocketHandler;

    @Mock
    private EmailService emailService;

    @Mock
    private NotificationHistoryService historyService;

    @InjectMocks
    private NotificationListener listener;

    private final UUID orderId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("handleOrderConfirmed sends email, broadcasts via WebSocket, and stores history")
    void handleOrderConfirmed_sendsEmailAndBroadcasts() {
        var event = new OrderConfirmed(orderId, userId, Instant.now());
        var record = new ConsumerRecord<String, Object>("orders", 0, 0, orderId.toString(), event);

        listener.handleOrderEvents(record);

        verify(emailService).sendOrderConfirmedEmail(event);
        verify(webSocketHandler).broadcastOrderUpdate(eq(orderId.toString()), eq("CONFIRMED"), anyString());
        verify(historyService).store(eq(orderId), eq(userId), eq("ORDER_CONFIRMED"), eq("EMAIL"), anyString());
        verify(historyService).store(eq(orderId), eq(userId), eq("ORDER_CONFIRMED"), eq("WEBSOCKET"), anyString());
    }

    @Test
    @DisplayName("handleOrderCancelled sends email, broadcasts via WebSocket, and stores history")
    void handleOrderCancelled_sendsEmailAndBroadcasts() {
        var event = new OrderCancelled(orderId, userId, "Out of stock", Instant.now());
        var record = new ConsumerRecord<String, Object>("orders", 0, 0, orderId.toString(), event);

        listener.handleOrderEvents(record);

        verify(emailService).sendOrderCancelledEmail(event);
        verify(webSocketHandler).broadcastOrderUpdate(eq(orderId.toString()), eq("CANCELLED"), contains("Out of stock"));
        verify(historyService).store(eq(orderId), eq(userId), eq("ORDER_CANCELLED"), eq("EMAIL"), anyString());
        verify(historyService).store(eq(orderId), eq(userId), eq("ORDER_CANCELLED"), eq("WEBSOCKET"), anyString());
    }

    @Test
    @DisplayName("handlePaymentFailed sends email, broadcasts via WebSocket, and stores history")
    void handlePaymentFailed_sendsEmailAndBroadcasts() {
        var event = new PaymentFailed(orderId, userId, "Card declined", Instant.now());
        var record = new ConsumerRecord<String, Object>("orders", 0, 0, orderId.toString(), event);

        listener.handleOrderEvents(record);

        verify(emailService).sendPaymentFailedEmail(event);
        verify(webSocketHandler).broadcastOrderUpdate(eq(orderId.toString()), eq("PAYMENT_FAILED"), contains("Card declined"));
        verify(historyService).store(eq(orderId), eq(userId), eq("PAYMENT_FAILED"), eq("EMAIL"), anyString());
        verify(historyService).store(eq(orderId), eq(userId), eq("PAYMENT_FAILED"), eq("WEBSOCKET"), anyString());
    }

    @Test
    @DisplayName("handleUnknownEvent logs warning without sending notifications")
    void handleUnknownEvent_logsWarning() {
        var unknownEvent = "unexpected payload";
        var record = new ConsumerRecord<String, Object>("orders", 0, 0, "key", unknownEvent);

        listener.handleOrderEvents(record);

        verifyNoInteractions(emailService);
        verifyNoInteractions(webSocketHandler);
        verifyNoInteractions(historyService);
    }
}
