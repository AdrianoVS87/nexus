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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderSagaListener")
class OrderSagaListenerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher eventPublisher;

    @InjectMocks
    private OrderSagaListener sagaListener;

    private UUID orderId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    private Order orderInStatus(OrderStatus status) {
        return Order.builder()
                .id(orderId)
                .userId(userId)
                .status(status)
                .totalAmount(new BigDecimal("100.00"))
                .currency("USD")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("PaymentCompleted")
    class PaymentCompletedTests {

        @Test
        @DisplayName("transitions PAYMENT_REQUESTED → INVENTORY_REQUESTED and publishes event")
        void happyPath() {
            var order = orderInStatus(OrderStatus.PAYMENT_REQUESTED);
            var event = new PaymentCompleted(UUID.randomUUID(), orderId, new BigDecimal("100.00"), Instant.now());
            var record = new ConsumerRecord<String, Object>("payments", 0, 0, orderId.toString(), event);

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            sagaListener.handlePaymentEvents(record);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.INVENTORY_REQUESTED);
            verify(orderRepository, times(1)).save(order);
            verify(eventPublisher).publishInventoryReserveRequested(order);
        }

        @Test
        @DisplayName("ignores replay when order already past PAYMENT_REQUESTED")
        void replayIdempotency() {
            var order = orderInStatus(OrderStatus.CONFIRMED);
            var event = new PaymentCompleted(UUID.randomUUID(), orderId, new BigDecimal("100.00"), Instant.now());
            var record = new ConsumerRecord<String, Object>("payments", 0, 0, orderId.toString(), event);

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            sagaListener.handlePaymentEvents(record);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(orderRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }
    }

    @Nested
    @DisplayName("PaymentFailed")
    class PaymentFailedTests {

        @Test
        @DisplayName("transitions PAYMENT_REQUESTED → CANCELLED and publishes cancellation")
        void happyPath() {
            var order = orderInStatus(OrderStatus.PAYMENT_REQUESTED);
            var event = new PaymentFailed(orderId, "Insufficient funds", Instant.now());
            var record = new ConsumerRecord<String, Object>("payments", 0, 0, orderId.toString(), event);

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            sagaListener.handlePaymentEvents(record);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(eventPublisher).publishOrderCancelled(eq(orderId), eq(userId), contains("Insufficient funds"));
        }

        @Test
        @DisplayName("ignores replay when order already cancelled")
        void replayIdempotency() {
            var order = orderInStatus(OrderStatus.CANCELLED);
            var event = new PaymentFailed(orderId, "Insufficient funds", Instant.now());
            var record = new ConsumerRecord<String, Object>("payments", 0, 0, orderId.toString(), event);

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            sagaListener.handlePaymentEvents(record);

            verify(orderRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }
    }

    @Nested
    @DisplayName("InventoryReserved")
    class InventoryReservedTests {

        @Test
        @DisplayName("transitions INVENTORY_REQUESTED → CONFIRMED and publishes confirmation")
        void happyPath() {
            var order = orderInStatus(OrderStatus.INVENTORY_REQUESTED);
            var event = new InventoryReserved(orderId, Instant.now());
            var record = new ConsumerRecord<String, Object>("inventory", 0, 0, orderId.toString(), event);

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            sagaListener.handleInventoryEvents(record);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(eventPublisher).publishOrderConfirmed(orderId, userId);
        }
    }

    @Nested
    @DisplayName("InventoryInsufficient")
    class InventoryInsufficientTests {

        @Test
        @DisplayName("transitions INVENTORY_REQUESTED → REFUND_REQUESTED → CANCELLED with correct event ordering")
        void compensationFlow() {
            var order = orderInStatus(OrderStatus.INVENTORY_REQUESTED);
            var event = new InventoryInsufficient(orderId, UUID.randomUUID(), "Out of stock", Instant.now());
            var record = new ConsumerRecord<String, Object>("inventory", 0, 0, orderId.toString(), event);

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            sagaListener.handleInventoryEvents(record);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

            InOrder inOrder = inOrder(eventPublisher);
            inOrder.verify(eventPublisher).publishPaymentRefundRequested(any(Order.class), contains("Out of stock"));
            inOrder.verify(eventPublisher).publishOrderCancelled(eq(orderId), eq(userId), contains("Out of stock"));
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("throws OrderNotFoundException for non-existent order")
        void orderNotFound() {
            var event = new PaymentCompleted(UUID.randomUUID(), orderId, new BigDecimal("100.00"), Instant.now());
            var record = new ConsumerRecord<String, Object>("payments", 0, 0, orderId.toString(), event);

            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sagaListener.handlePaymentEvents(record))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("ignores unknown event types without side effects")
        void unknownEvent() {
            var record = new ConsumerRecord<String, Object>("payments", 0, 0, orderId.toString(), "unknown");

            sagaListener.handlePaymentEvents(record);

            verifyNoInteractions(eventPublisher);
            verify(orderRepository, never()).findById(any());
        }
    }
}
