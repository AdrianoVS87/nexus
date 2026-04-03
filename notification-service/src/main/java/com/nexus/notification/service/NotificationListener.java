package com.nexus.notification.service;

import com.nexus.notification.domain.event.OrderCancelled;
import com.nexus.notification.domain.event.OrderConfirmed;
import com.nexus.notification.websocket.OrderStatusWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationListener {

    private final OrderStatusWebSocketHandler webSocketHandler;

    @KafkaListener(topics = "orders", groupId = "notification-service")
    public void handleOrderEvents(Object event) {
        if (event instanceof OrderConfirmed oc) {
            handleOrderConfirmed(oc);
        } else if (event instanceof OrderCancelled cancelled) {
            handleOrderCancelled(cancelled);
        }
    }

    private void handleOrderConfirmed(OrderConfirmed event) {
        log.info("📧 Sending confirmation email for orderId={} (simulated)", event.orderId());
        webSocketHandler.broadcastOrderUpdate(event.orderId().toString(), "CONFIRMED");
    }

    private void handleOrderCancelled(OrderCancelled event) {
        log.info("📧 Sending cancellation email for orderId={}, reason={} (simulated)", event.orderId(), event.reason());
        webSocketHandler.broadcastOrderUpdate(event.orderId().toString(), "CANCELLED");
    }
}
