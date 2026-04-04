package com.nexus.order.saga;

import com.nexus.order.domain.enums.OrderStatus;
import com.nexus.order.domain.event.*;
import com.nexus.order.repository.OrderRepository;
import com.nexus.order.service.OrderEventPublisher;
import com.nexus.order.service.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Saga orchestrator that drives order state transitions in response to
 * domain events from the payment and inventory services.
 *
 * <p>Every handler validates the expected pre-condition status before
 * mutating state, making the saga idempotent and replay-safe.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaListener {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    @KafkaListener(topics = "payments", groupId = "order-saga")
    @Transactional
    public void handlePaymentEvents(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        log.info("Saga received payment event: type={}", event.getClass().getSimpleName());
        if (event instanceof PaymentCompleted pc) {
            handlePaymentCompleted(pc);
        } else if (event instanceof PaymentFailed pf) {
            handlePaymentFailed(pf);
        }
    }

    @KafkaListener(topics = "inventory", groupId = "order-saga")
    @Transactional
    public void handleInventoryEvents(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        log.info("Saga received inventory event: type={}", event.getClass().getSimpleName());
        if (event instanceof InventoryReserved ir) {
            handleInventoryReserved(ir);
        } else if (event instanceof InventoryInsufficient ii) {
            handleInventoryInsufficient(ii);
        }
    }

    private void handlePaymentCompleted(PaymentCompleted event) {
        var order = findOrder(event.orderId());

        if (order.getStatus() != OrderStatus.PAYMENT_REQUESTED) {
            log.warn("Saga: ignoring PaymentCompleted for orderId={} — expected PAYMENT_REQUESTED, got {}",
                    event.orderId(), order.getStatus());
            return;
        }

        log.info("Saga: PaymentCompleted for orderId={}", event.orderId());
        order.transitionTo(OrderStatus.PAYMENT_COMPLETED);
        order.transitionTo(OrderStatus.INVENTORY_REQUESTED);
        orderRepository.save(order);

        eventPublisher.publishInventoryReserveRequested(order);
    }

    private void handlePaymentFailed(PaymentFailed event) {
        var order = findOrder(event.orderId());

        if (order.getStatus() != OrderStatus.PAYMENT_REQUESTED) {
            log.warn("Saga: ignoring PaymentFailed for orderId={} — expected PAYMENT_REQUESTED, got {}",
                    event.orderId(), order.getStatus());
            return;
        }

        log.info("Saga: PaymentFailed for orderId={}, reason={}", event.orderId(), event.reason());
        order.transitionTo(OrderStatus.CANCELLED);
        orderRepository.save(order);

        eventPublisher.publishOrderCancelled(order.getId(), order.getUserId(),
                "Payment failed: " + event.reason());
    }

    private void handleInventoryReserved(InventoryReserved event) {
        var order = findOrder(event.orderId());

        if (order.getStatus() != OrderStatus.INVENTORY_REQUESTED) {
            log.warn("Saga: ignoring InventoryReserved for orderId={} — expected INVENTORY_REQUESTED, got {}",
                    event.orderId(), order.getStatus());
            return;
        }

        log.info("Saga: InventoryReserved for orderId={}", event.orderId());
        order.transitionTo(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        eventPublisher.publishOrderConfirmed(order.getId(), order.getUserId());
    }

    private void handleInventoryInsufficient(InventoryInsufficient event) {
        var order = findOrder(event.orderId());

        if (order.getStatus() != OrderStatus.INVENTORY_REQUESTED) {
            log.warn("Saga: ignoring InventoryInsufficient for orderId={} — expected INVENTORY_REQUESTED, got {}",
                    event.orderId(), order.getStatus());
            return;
        }

        log.info("Saga: InventoryInsufficient for orderId={}, reason={}", event.orderId(), event.reason());
        order.transitionTo(OrderStatus.REFUND_REQUESTED);
        eventPublisher.publishPaymentRefundRequested(order, "Inventory insufficient: " + event.reason());

        order.transitionTo(OrderStatus.CANCELLED);
        orderRepository.save(order);

        eventPublisher.publishOrderCancelled(order.getId(), order.getUserId(),
                "Inventory insufficient: " + event.reason());
    }

    private com.nexus.order.domain.entity.Order findOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
