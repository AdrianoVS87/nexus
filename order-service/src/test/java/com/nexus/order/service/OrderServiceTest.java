package com.nexus.order.service;

import com.nexus.order.domain.entity.Order;
import com.nexus.order.domain.enums.OrderStatus;
import com.nexus.order.dto.CreateOrderRequest;
import com.nexus.order.dto.CreateOrderRequest.OrderItemRequest;
import com.nexus.order.dto.OrderResponse;
import com.nexus.order.metrics.SagaMetrics;
import com.nexus.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher eventPublisher;

    @Mock
    private SagaMetrics sagaMetrics;

    @InjectMocks
    private OrderService orderService;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    private UUID userId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        productId = UUID.randomUUID();
    }

    /**
     * Helper: builds an Order with all required fields pre-set (avoids JPA lifecycle callbacks in unit tests).
     */
    private Order buildOrder(UUID id, OrderStatus status) {
        return Order.builder()
                .id(id)
                .userId(userId)
                .status(status)
                .totalAmount(new BigDecimal("100.00"))
                .currency("USD")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /** Simulates JPA @PrePersist for save mock. */
    private Order simulatePersist(Order order) {
        // In tests the builder already sets fields, but createOrder builds without createdAt/currency
        if (order.getCreatedAt() == null) {
            // Use reflection-free approach: the builder sets these via @Builder.Default or @PrePersist
            // In unit tests without JPA, we rely on the builder having set them.
        }
        return order;
    }

    @Nested
    @DisplayName("createOrder")
    class CreateOrderTests {

        @Test
        @DisplayName("creates order in PAYMENT_REQUESTED status with correct total")
        void happyPath() {
            var request = new CreateOrderRequest(userId, List.of(
                    new OrderItemRequest(productId, "Keyboard", 2, new BigDecimal("10.00")),
                    new OrderItemRequest(UUID.randomUUID(), "Mouse", 3, new BigDecimal("5.50"))
            ));

            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            OrderResponse response = orderService.createOrder(request);

            assertThat(response.status()).isEqualTo(OrderStatus.PAYMENT_REQUESTED);
            assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("36.50"));
            assertThat(response.items()).hasSize(2);
            verify(eventPublisher).publishOrderCreated(any(Order.class));
            verify(orderRepository, times(1)).save(any(Order.class));
        }

        @Test
        @DisplayName("publishes OrderCreated event after save")
        void publishesEvent() {
            var request = new CreateOrderRequest(userId, List.of(
                    new OrderItemRequest(productId, "Test", 1, new BigDecimal("25.00"))
            ));

            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            orderService.createOrder(request);

            verify(eventPublisher).publishOrderCreated(any(Order.class));
        }
    }

    @Nested
    @DisplayName("getOrder")
    class GetOrderTests {

        @Test
        @DisplayName("returns order when found")
        void found() {
            UUID orderId = UUID.randomUUID();
            var order = buildOrder(orderId, OrderStatus.CONFIRMED);
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            OrderResponse response = orderService.getOrder(orderId);

            assertThat(response.id()).isEqualTo(orderId);
            assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        @DisplayName("throws OrderNotFoundException when not found")
        void notFound() {
            UUID orderId = UUID.randomUUID();
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrder(orderId))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining(orderId.toString());
        }
    }

    @Nested
    @DisplayName("getOrdersByUser")
    class GetOrdersByUserTests {

        @Test
        @DisplayName("returns paginated results")
        void paginated() {
            var pageable = PageRequest.of(0, 10);
            var order = buildOrder(UUID.randomUUID(), OrderStatus.PENDING);

            when(orderRepository.findByUserId(userId, pageable))
                    .thenReturn(new PageImpl<>(List.of(order), pageable, 1));

            var result = orderService.getOrdersByUser(userId, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("updateOrderStatus")
    class UpdateOrderStatusTests {

        @Test
        @DisplayName("valid transition PENDING → PAYMENT_REQUESTED succeeds")
        void validTransition() {
            UUID orderId = UUID.randomUUID();
            var order = buildOrder(orderId, OrderStatus.PENDING);

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            orderService.updateOrderStatus(orderId, OrderStatus.PAYMENT_REQUESTED);

            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.PAYMENT_REQUESTED);
        }

        @Test
        @DisplayName("invalid transition PENDING → CONFIRMED throws IllegalStateException")
        void invalidTransition() {
            UUID orderId = UUID.randomUUID();
            var order = buildOrder(orderId, OrderStatus.PENDING);

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Invalid order transition");
        }

        @Test
        @DisplayName("throws OrderNotFoundException for non-existent order")
        void notFound() {
            UUID orderId = UUID.randomUUID();
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }
}
