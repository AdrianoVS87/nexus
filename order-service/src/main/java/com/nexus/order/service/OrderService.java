package com.nexus.order.service;

import com.nexus.order.domain.entity.Order;
import com.nexus.order.domain.entity.OrderItem;
import com.nexus.order.domain.enums.OrderStatus;
import com.nexus.order.dto.CreateOrderRequest;
import com.nexus.order.dto.OrderResponse;
import com.nexus.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        var order = Order.builder()
                .id(UUID.randomUUID())
                .userId(request.userId())
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (var itemReq : request.items()) {
            var subtotal = itemReq.unitPrice().multiply(BigDecimal.valueOf(itemReq.quantity()));
            var item = OrderItem.builder()
                    .id(UUID.randomUUID())
                    .productId(itemReq.productId())
                    .productName(itemReq.productName())
                    .quantity(itemReq.quantity())
                    .unitPrice(itemReq.unitPrice())
                    .subtotal(subtotal)
                    .build();
            order.addItem(item);
            total = total.add(subtotal);
        }
        order.setTotalAmount(total);

        // Transition PENDING → PAYMENT_REQUESTED in one save
        order.transitionTo(OrderStatus.PAYMENT_REQUESTED);
        order = orderRepository.save(order);
        log.info("Order created: orderId={}, userId={}, total={}", order.getId(), order.getUserId(), total);

        eventPublisher.publishOrderCreated(order);

        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersByUser(UUID userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable)
                .map(OrderResponse::from);
    }

    @Transactional
    public void updateOrderStatus(UUID orderId, OrderStatus target) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.transitionTo(target);
        orderRepository.save(order);
        log.info("Order status updated: orderId={}, status={}", orderId, target);
    }
}
