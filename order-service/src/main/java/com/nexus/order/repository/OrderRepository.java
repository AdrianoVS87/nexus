package com.nexus.order.repository;

import com.nexus.order.domain.entity.Order;
import com.nexus.order.domain.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    Page<Order> findByUserId(UUID userId, Pageable pageable);
    long countByStatus(OrderStatus status);
}
