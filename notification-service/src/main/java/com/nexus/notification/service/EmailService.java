package com.nexus.notification.service;

import com.nexus.notification.domain.event.OrderCancelled;
import com.nexus.notification.domain.event.OrderConfirmed;
import com.nexus.notification.domain.event.PaymentFailed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class EmailService {

    public void sendOrderConfirmedEmail(OrderConfirmed event) {
        log.info("{}", buildEmailJson(
                event.orderId(),
                event.userId(),
                "ORDER_CONFIRMED",
                "Order Confirmation",
                "Your order %s has been confirmed. Thank you for your purchase!".formatted(event.orderId()),
                event.timestamp()
        ));
    }

    public void sendOrderCancelledEmail(OrderCancelled event) {
        log.info("{}", buildEmailJson(
                event.orderId(),
                event.userId(),
                "ORDER_CANCELLED",
                "Order Cancellation",
                "Your order %s has been cancelled. Reason: %s".formatted(event.orderId(), event.reason()),
                event.timestamp()
        ));
    }

    public void sendPaymentFailedEmail(PaymentFailed event) {
        log.info("{}", buildEmailJson(
                event.orderId(),
                event.userId(),
                "PAYMENT_FAILED",
                "Payment Failed",
                "Payment for order %s has failed. Reason: %s. Please update your payment method.".formatted(event.orderId(), event.reason()),
                event.timestamp()
        ));
    }

    private String buildEmailJson(UUID orderId, UUID userId, String type, String subject, String body, Instant eventTimestamp) {
        return """
                {"email":{"to":"user-%s@nexus.com","subject":"%s","body":"%s","metadata":{"orderId":"%s","type":"%s","eventTimestamp":"%s","sentAt":"%s"}}}"""
                .formatted(userId, subject, body, orderId, type, eventTimestamp, Instant.now());
    }
}
