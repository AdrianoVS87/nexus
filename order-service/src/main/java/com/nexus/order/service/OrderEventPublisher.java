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

    private static final String TOPIC_ORDERS = "orders";
    private static final String TOPIC_PAYMENTS = "payments";
    private static final String TOPIC_INVENTORY = "inventory";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publishes an OrderCreated event followed by a PaymentRequested event to kick off the saga.
     */
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
        sendEvent(TOPIC_ORDERS, order.getId().toString(), event, "OrderCreated");

        var paymentEvent = new PaymentRequested(
                order.getId(),
                order.getUserId(),
                order.getTotalAmount(),
                order.getCurrency(),
                UUID.randomUUID().toString(),
                Instant.now()
        );
        sendEvent(TOPIC_PAYMENTS, order.getId().toString(), paymentEvent, "PaymentRequested");
    }

    /**
     * Publishes an inventory reservation request for the given order's items.
     */
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
        sendEvent(TOPIC_INVENTORY, order.getId().toString(), event, "InventoryReserveRequested");
    }

    /**
     * Publishes an order confirmation event.
     */
    public void publishOrderConfirmed(UUID orderId, UUID userId) {
        var event = new OrderConfirmed(orderId, userId, Instant.now());
        sendEvent(TOPIC_ORDERS, orderId.toString(), event, "OrderConfirmed");
    }

    /**
     * Publishes an order cancellation event.
     */
    public void publishOrderCancelled(UUID orderId, UUID userId, String reason) {
        var event = new OrderCancelled(orderId, userId, reason, Instant.now());
        sendEvent(TOPIC_ORDERS, orderId.toString(), event, "OrderCancelled");
    }

    /**
     * Publishes a payment refund request for the given order.
     */
    public void publishPaymentRefundRequested(Order order, String reason) {
        var event = new PaymentRefundRequested(
                order.getId(),
                order.getUserId(),
                order.getTotalAmount(),
                reason,
                Instant.now()
        );
        sendEvent(TOPIC_PAYMENTS, order.getId().toString(), event, "PaymentRefundRequested");
    }

    private void sendEvent(String topic, String key, Object event, String eventType) {
        kafkaTemplate.send(topic, key, event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish {} to topic={}, key={}: {}", eventType, topic, key, ex.getMessage(), ex);
            } else {
                log.info("Published {}: topic={}, key={}, offset={}", eventType, topic, key,
                        result.getRecordMetadata().offset());
            }
        });
    }
}
