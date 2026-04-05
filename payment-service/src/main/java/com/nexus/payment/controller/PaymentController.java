package com.nexus.payment.controller;

import com.nexus.payment.domain.entity.Payment;
import com.nexus.payment.exception.PaymentNotFoundException;
import com.nexus.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentRepository paymentRepository;

    @GetMapping("/{orderId}")
    public ResponseEntity<PaymentResponse> getPaymentByOrderId(@PathVariable UUID orderId) {
        var payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));

        return ResponseEntity.ok(PaymentResponse.from(payment));
    }

    public record PaymentResponse(
            UUID id,
            UUID orderId,
            UUID userId,
            java.math.BigDecimal amount,
            String currency,
            String status,
            java.time.Instant createdAt,
            java.time.Instant updatedAt
    ) {
        public static PaymentResponse from(Payment payment) {
            return new PaymentResponse(
                    payment.getId(),
                    payment.getOrderId(),
                    payment.getUserId(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getStatus().name(),
                    payment.getCreatedAt(),
                    payment.getUpdatedAt()
            );
        }
    }
}
