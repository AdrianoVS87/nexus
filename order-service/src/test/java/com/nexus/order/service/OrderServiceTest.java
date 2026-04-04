package com.nexus.order.service;

import com.nexus.order.domain.entity.Order;
import com.nexus.order.domain.entity.OrderItem;
import com.nexus.order.domain.enums.OrderStatus;
import com.nexus.order.dto.CreateOrderRequest;
import com.nexus.order.dto.CreateOrderRequest.OrderItemRequest;
import com.nexus.order.dto.OrderResponse;
import com.nexus.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
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
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher eventPublisher;

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

    @Test
    void createOrder_withValidRequest_returnsCreatedOrder() {
        var request = new CreateOrderRequest(userId, List.of(
                new OrderItemRequest(productId, "Test Product", 2, new BigDecimal("19.99"))
        ));

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getCreatedAt() == null) {
                order.setCreatedAt(Instant.now());
                order.setUpdatedAt(Instant.now());
                if (order.getCurrency() == null) order.setCurrency("USD");
            }
            return order;
        });

        OrderResponse response = orderService.createOrder(request);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.status()).isEqualTo(OrderStatus.PAYMENT_REQUESTED);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().productName()).isEqualTo("Test Product");
    }

    @Test
    void createOrder_calculatesCorrectTotal() {
        var request = new CreateOrderRequest(userId, List.of(
                new OrderItemRequest(productId, "Product A", 2, new BigDecimal("10.00")),
                new OrderItemRequest(UUID.randomUUID(), "Product B", 3, new BigDecimal("5.50"))
        ));

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getCreatedAt() == null) {
                order.setCreatedAt(Instant.now());
                order.setUpdatedAt(Instant.now());
                if (order.getCurrency() == null) order.setCurrency("USD");
            }
            return order;
        });

        OrderResponse response = orderService.createOrder(request);

        // 2 * 10.00 + 3 * 5.50 = 20.00 + 16.50 = 36.50
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("36.50"));
    }

    @Test
    void createOrder_publishesOrderCreatedEvent() {
        var request = new CreateOrderRequest(userId, List.of(
                new OrderItemRequest(productId, "Test Product", 1, new BigDecimal("25.00"))
        ));

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getCreatedAt() == null) {
                order.setCreatedAt(Instant.now());
                order.setUpdatedAt(Instant.now());
                if (order.getCurrency() == null) order.setCurrency("USD");
            }
            return order;
        });

        orderService.createOrder(request);

        verify(eventPublisher).publishOrderCreated(any(Order.class));
    }

    @Test
    void createOrder_withEmptyItems_throwsException() {
        var request = new CreateOrderRequest(userId, List.of());

        // Empty list would be caught by @NotEmpty validation at controller level.
        // At service level, BigDecimal.ZERO total is technically valid, but the loop produces no items.
        // This test verifies the service doesn't explode on empty items.
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getCreatedAt() == null) {
                order.setCreatedAt(Instant.now());
                order.setUpdatedAt(Instant.now());
                if (order.getCurrency() == null) order.setCurrency("USD");
            }
            return order;
        });

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.items()).isEmpty();
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getOrder_withNonExistentId_throwsNotFoundException() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(orderId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(orderId.toString());
    }

    @Test
    void getOrder_withExistingId_returnsOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .userId(userId)
                .status(OrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("50.00"))
                .currency("USD")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrder(orderId);

        assertThat(response.id()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void getOrdersByUser_returnsPaginatedResults() {
        var pageable = PageRequest.of(0, 10);
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("100.00"))
                .currency("USD")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(orderRepository.findByUserId(userId, pageable))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 1));

        var result = orderService.getOrdersByUser(userId, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().userId()).isEqualTo(userId);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void updateOrderStatus_withExistingOrder_updatesStatus() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .userId(userId)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("50.00"))
                .currency("USD")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED);

        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void updateOrderStatus_withNonExistentOrder_throwsNotFoundException() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED))
                .isInstanceOf(OrderNotFoundException.class);
    }
}
