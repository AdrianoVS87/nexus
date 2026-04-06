package com.nexus.order.controller;

import com.nexus.order.dto.CancelOrderRequest;
import com.nexus.order.dto.CreateOrderRequest;
import com.nexus.order.dto.OrderResponse;
import com.nexus.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        var response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable UUID id,
            @RequestBody(required = false) CancelOrderRequest request
    ) {
        String reason = (request != null && request.reason() != null) ? request.reason() : "No reason provided";
        return ResponseEntity.ok(orderService.cancelOrder(id, reason));
    }

    @GetMapping
    public ResponseEntity<Page<OrderResponse>> getOrdersByUser(
            @RequestParam UUID userId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(orderService.getOrdersByUser(userId, pageable));
    }
}
