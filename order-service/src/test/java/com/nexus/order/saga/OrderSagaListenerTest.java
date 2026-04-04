package com.nexus.order.saga;

import com.nexus.order.domain.entity.Order;
import com.nexus.order.domain.enums.OrderStatus;
import com.nexus.order.domain.event.InventoryInsufficient;
import com.nexus.order.domain.event.InventoryReserved;
import com.nexus.order.domain.event.PaymentCompleted;
import com.nexus.order.domain.event.PaymentFailed;
import com.nexus.order.repository.OrderRepository;
import com.nexus.order.service.OrderEventPublisher;
import com.nexus.order.service.OrderNotFoundException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderSagaListenerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher eventPublisher;

    @InjectMocks
    private OrderSagaListener sagaListener;

    private UUID orderId;
    private UUID userId;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
        testOrder = Order.builder()
                .id(orderId)
                .userId(userId)
                .status(OrderStatus.PAYMENT_REQUESTED)
                .totalAmount(new BigDecimal("100.00"))
                .currency("USD")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void handlePaymentCompleted_updatesStatusAndPublishesInventoryRequest() {
        var event = new PaymentCompleted(UUID.randomUUID(), orderId, new BigDecimal("100.00"), Instant.now());
        var record = new ConsumerRecord<String, Object>("payments", 0, 0, orderId.toString(), event);

        // Track status transitions
        List<OrderStatus> statusTransitions = new ArrayList<>();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            statusTransitions.add(o.getStatus());
            return o;
        });

        sagaListener.handlePaymentEvents(record);

        assertThat(statusTransitions).containsExactly(
                OrderStatus.PAYMENT_COMPLETED,
                OrderStatus.INVENTORY_REQUESTED
        );
        verify(eventPublisher).publishInventoryReserveRequested(any(Order.class));
    }

    @Test
    void handlePaymentFailed_cancelsOrder() {
        var event = new PaymentFailed(orderId, "Insufficient funds", Instant.now());
        var record = new ConsumerRecord<String, Object>("payments", 0, 0, orderId.toString(), event);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        sagaListener.handlePaymentEvents(record);

        assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(eventPublisher).publishOrderCancelled(eq(orderId), eq(userId), contains("Insufficient funds"));
    }

    @Test
    void handleInventoryReserved_confirmsOrder() {
        testOrder.setStatus(OrderStatus.INVENTORY_REQUESTED);
        var event = new InventoryReserved(orderId, Instant.now());
        var record = new ConsumerRecord<String, Object>("inventory", 0, 0, orderId.toString(), event);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        sagaListener.handleInventoryEvents(record);

        assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(eventPublisher).publishOrderConfirmed(orderId, userId);
    }

    @Test
    void handleInventoryInsufficient_requestsRefundAndCancels() {
        testOrder.setStatus(OrderStatus.INVENTORY_REQUESTED);
        var event = new InventoryInsufficient(orderId, UUID.randomUUID(), "Out of stock", Instant.now());
        var record = new ConsumerRecord<String, Object>("inventory", 0, 0, orderId.toString(), event);

        List<OrderStatus> statusTransitions = new ArrayList<>();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            statusTransitions.add(o.getStatus());
            return o;
        });

        sagaListener.handleInventoryEvents(record);

        assertThat(statusTransitions).containsExactly(
                OrderStatus.REFUND_REQUESTED,
                OrderStatus.CANCELLED
        );

        InOrder inOrder = inOrder(eventPublisher);
        inOrder.verify(eventPublisher).publishPaymentRefundRequested(any(Order.class), contains("Out of stock"));
        inOrder.verify(eventPublisher).publishOrderCancelled(eq(orderId), eq(userId), contains("Out of stock"));
    }

    @Test
    void handlePaymentCompleted_withNonExistentOrder_throwsNotFoundException() {
        var event = new PaymentCompleted(UUID.randomUUID(), orderId, new BigDecimal("100.00"), Instant.now());
        var record = new ConsumerRecord<String, Object>("payments", 0, 0, orderId.toString(), event);

        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sagaListener.handlePaymentEvents(record))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void handlePaymentEvents_ignoresUnknownEventTypes() {
        var record = new ConsumerRecord<String, Object>("payments", 0, 0, orderId.toString(), "unknown-event");

        sagaListener.handlePaymentEvents(record);

        verifyNoInteractions(eventPublisher);
        verify(orderRepository, never()).findById(any());
    }
}
