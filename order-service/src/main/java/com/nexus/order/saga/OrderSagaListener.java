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
        log.info("Saga: PaymentCompleted for orderId={}", event.orderId());
        var order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new OrderNotFoundException(event.orderId()));

        order.setStatus(OrderStatus.PAYMENT_COMPLETED);
        orderRepository.save(order);

        order.setStatus(OrderStatus.INVENTORY_REQUESTED);
        orderRepository.save(order);
        eventPublisher.publishInventoryReserveRequested(order);
    }

    private void handlePaymentFailed(PaymentFailed event) {
        log.info("Saga: PaymentFailed for orderId={}, reason={}", event.orderId(), event.reason());
        var order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new OrderNotFoundException(event.orderId()));

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        eventPublisher.publishOrderCancelled(order.getId(), order.getUserId(), "Payment failed: " + event.reason());
    }

    private void handleInventoryReserved(InventoryReserved event) {
        log.info("Saga: InventoryReserved for orderId={}", event.orderId());
        var order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new OrderNotFoundException(event.orderId()));

        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        eventPublisher.publishOrderConfirmed(order.getId(), order.getUserId());
    }

    private void handleInventoryInsufficient(InventoryInsufficient event) {
        log.info("Saga: InventoryInsufficient for orderId={}, reason={}", event.orderId(), event.reason());
        var order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new OrderNotFoundException(event.orderId()));

        order.setStatus(OrderStatus.REFUND_REQUESTED);
        orderRepository.save(order);
        eventPublisher.publishPaymentRefundRequested(order, "Inventory insufficient: " + event.reason());

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        eventPublisher.publishOrderCancelled(order.getId(), order.getUserId(), "Inventory insufficient: " + event.reason());
    }
}
