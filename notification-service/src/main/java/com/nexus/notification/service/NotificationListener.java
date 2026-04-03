package com.nexus.notification.service;

import com.nexus.notification.domain.event.OrderCancelled;
import com.nexus.notification.domain.event.OrderConfirmed;
import com.nexus.notification.domain.event.PaymentFailed;
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
    private final EmailService emailService;
    private final NotificationHistoryService historyService;

    @KafkaListener(topics = "orders", groupId = "notification-service")
    public void handleOrderEvents(Object event) {
        if (event instanceof OrderConfirmed oc) {
            handleOrderConfirmed(oc);
        } else if (event instanceof OrderCancelled cancelled) {
            handleOrderCancelled(cancelled);
        } else if (event instanceof PaymentFailed pf) {
            handlePaymentFailed(pf);
        } else {
            log.warn("Received unknown event type: {}", event.getClass().getName());
        }
    }

    private void handleOrderConfirmed(OrderConfirmed event) {
        log.info("Processing OrderConfirmed for orderId={}", event.orderId());

        emailService.sendOrderConfirmedEmail(event);
        webSocketHandler.broadcastOrderUpdate(
                event.orderId().toString(),
                "CONFIRMED",
                "Your order has been confirmed"
        );
        historyService.store(event.orderId(), event.userId(), "ORDER_CONFIRMED", "EMAIL", "Order confirmation email sent");
        historyService.store(event.orderId(), event.userId(), "ORDER_CONFIRMED", "WEBSOCKET", "Real-time confirmation broadcast");
    }

    private void handleOrderCancelled(OrderCancelled event) {
        log.info("Processing OrderCancelled for orderId={}, reason={}", event.orderId(), event.reason());

        emailService.sendOrderCancelledEmail(event);
        webSocketHandler.broadcastOrderUpdate(
                event.orderId().toString(),
                "CANCELLED",
                "Your order has been cancelled: " + event.reason()
        );
        historyService.store(event.orderId(), event.userId(), "ORDER_CANCELLED", "EMAIL", "Order cancellation email sent");
        historyService.store(event.orderId(), event.userId(), "ORDER_CANCELLED", "WEBSOCKET", "Real-time cancellation broadcast");
    }

    private void handlePaymentFailed(PaymentFailed event) {
        log.info("Processing PaymentFailed for orderId={}, reason={}", event.orderId(), event.reason());

        emailService.sendPaymentFailedEmail(event);
        webSocketHandler.broadcastOrderUpdate(
                event.orderId().toString(),
                "PAYMENT_FAILED",
                "Payment failed: " + event.reason()
        );
        historyService.store(event.orderId(), event.userId(), "PAYMENT_FAILED", "EMAIL", "Payment failed email sent");
        historyService.store(event.orderId(), event.userId(), "PAYMENT_FAILED", "WEBSOCKET", "Real-time payment failure broadcast");
    }
}
