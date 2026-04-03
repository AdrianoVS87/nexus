package com.nexus.payment.service;

import com.nexus.payment.domain.entity.Payment;
import com.nexus.payment.domain.event.PaymentCompleted;
import com.nexus.payment.domain.event.PaymentFailed;
import com.nexus.payment.domain.event.PaymentRequested;
import com.nexus.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Random random = new Random();

    @Value("${payment.success-rate:0.8}")
    private double successRate;

    @KafkaListener(topics = "payments", groupId = "payment-service")
    @Transactional
    public void handlePaymentRequested(PaymentRequested event) {
        log.info("Received PaymentRequested: orderId={}, idempotencyKey={}", event.orderId(), event.idempotencyKey());

        // Idempotency check
        var existing = paymentRepository.findByIdempotencyKey(event.idempotencyKey());
        if (existing.isPresent()) {
            log.info("Duplicate payment request ignored: idempotencyKey={}", event.idempotencyKey());
            return;
        }

        var payment = Payment.builder()
                .orderId(event.orderId())
                .idempotencyKey(event.idempotencyKey())
                .amount(event.amount())
                .currency(event.currency())
                .build();

        // Simulate payment processing
        boolean success = random.nextDouble() < successRate;

        if (success) {
            payment.setStatus(Payment.PaymentStatus.COMPLETED);
            paymentRepository.save(payment);

            var completed = new PaymentCompleted(payment.getId(), event.orderId(), event.amount(), Instant.now());
            kafkaTemplate.send("payments", event.orderId().toString(), completed);
            log.info("Payment completed: paymentId={}, orderId={}", payment.getId(), event.orderId());
        } else {
            payment.setStatus(Payment.PaymentStatus.FAILED);
            paymentRepository.save(payment);

            var failed = new PaymentFailed(event.orderId(), "Payment declined by processor", Instant.now());
            kafkaTemplate.send("payments", event.orderId().toString(), failed);
            log.info("Payment failed: orderId={}", event.orderId());
        }
    }
}
