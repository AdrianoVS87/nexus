package com.nexus.order.service;

import com.nexus.order.domain.entity.Order;
import com.nexus.order.domain.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderCreated(Order order) {
        var event = new OrderCreated(
                order.getId(),
                order.getUserId(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getItems().stream()
                        .map(item -> new OrderCreated.OrderItemPayload(
                                item.getProductId(),
                                item.getProductName(),
                                item.getQuantity(),
                                item.getUnitPrice()
                        ))
                        .toList(),
                Instant.now()
        );
        kafkaTemplate.send("orders", order.getId().toString(), event);
        log.info("Published OrderCreated: orderId={}", order.getId());

        // Saga: immediately request payment
        var paymentEvent = new PaymentRequested(
                order.getId(),
                order.getUserId(),
                order.getTotalAmount(),
                order.getCurrency(),
                UUID.randomUUID().toString(),
                Instant.now()
        );
        kafkaTemplate.send("payments", order.getId().toString(), paymentEvent);
        log.info("Published PaymentRequested: orderId={}", order.getId());
    }

    public void publishInventoryReserveRequested(Order order) {
        var event = new InventoryReserveRequested(
                order.getId(),
                order.getItems().stream()
                        .map(item -> new InventoryReserveRequested.ReservationItem(
                                item.getProductId(),
                                item.getQuantity()
                        ))
                        .toList(),
                Instant.now()
        );
        kafkaTemplate.send("inventory", order.getId().toString(), event);
        log.info("Published InventoryReserveRequested: orderId={}", order.getId());
    }

    public void publishOrderConfirmed(UUID orderId, UUID userId) {
        var event = new OrderConfirmed(orderId, userId, Instant.now());
        kafkaTemplate.send("orders", orderId.toString(), event);
        log.info("Published OrderConfirmed: orderId={}", orderId);
    }

    public void publishOrderCancelled(UUID orderId, UUID userId, String reason) {
        var event = new OrderCancelled(orderId, userId, reason, Instant.now());
        kafkaTemplate.send("orders", orderId.toString(), event);
        log.info("Published OrderCancelled: orderId={}", orderId);
    }

    public void publishPaymentRefundRequested(Order order, String reason) {
        var event = new PaymentRefundRequested(
                order.getId(),
                order.getUserId(),
                order.getTotalAmount(),
                reason,
                Instant.now()
        );
        kafkaTemplate.send("payments", order.getId().toString(), event);
        log.info("Published PaymentRefundRequested: orderId={}", order.getId());
    }
}
