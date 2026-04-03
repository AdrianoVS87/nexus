package com.nexus.payment.service;

import com.nexus.payment.domain.entity.Payment;
import com.nexus.payment.domain.entity.Payment.PaymentStatus;
import com.nexus.payment.domain.event.PaymentCompleted;
import com.nexus.payment.domain.event.PaymentFailed;
import com.nexus.payment.domain.event.PaymentRefundRequested;
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

    @KafkaListener(topics = "payments", groupId = "payment-service", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void handlePaymentRequested(PaymentRequested event) {
        log.info("Received PaymentRequested: orderId={}, idempotencyKey={}", event.orderId(), event.idempotencyKey());

        // Idempotency check - if duplicate, re-publish existing result without reprocessing
        var existing = paymentRepository.findByIdempotencyKey(event.idempotencyKey());
        if (existing.isPresent()) {
            log.info("Duplicate payment request detected: idempotencyKey={}, status={}", event.idempotencyKey(), existing.get().getStatus());
            republishResult(existing.get());
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
            payment.setStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(payment);

            var completed = new PaymentCompleted(payment.getId(), event.orderId(), event.amount(), Instant.now());
            kafkaTemplate.send("payments", event.orderId().toString(), completed);
            log.info("Payment completed: paymentId={}, orderId={}", payment.getId(), event.orderId());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            var failed = new PaymentFailed(event.orderId(), "Payment declined by processor", Instant.now());
            kafkaTemplate.send("payments", event.orderId().toString(), failed);
            log.info("Payment failed: orderId={}", event.orderId());
        }
    }

    @KafkaListener(topics = "payments", groupId = "payment-service-refunds", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void handleRefundRequested(PaymentRefundRequested event) {
        log.info("Received PaymentRefundRequested: orderId={}", event.orderId());

        var payment = paymentRepository.findByOrderId(event.orderId());
        if (payment.isEmpty()) {
            log.warn("No payment found for refund: orderId={}", event.orderId());
            return;
        }

        var p = payment.get();
        if (p.getStatus() == PaymentStatus.REFUNDED) {
            log.info("Payment already refunded: orderId={}", event.orderId());
            return;
        }

        if (p.getStatus() != PaymentStatus.COMPLETED) {
            log.warn("Cannot refund payment in status {}: orderId={}", p.getStatus(), event.orderId());
            return;
        }

        p.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(p);
        log.info("Payment refunded: paymentId={}, orderId={}", p.getId(), event.orderId());
    }

    private void republishResult(Payment payment) {
        String orderId = payment.getOrderId().toString();
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            var completed = new PaymentCompleted(payment.getId(), payment.getOrderId(), payment.getAmount(), Instant.now());
            kafkaTemplate.send("payments", orderId, completed);
        } else if (payment.getStatus() == PaymentStatus.FAILED) {
            var failed = new PaymentFailed(payment.getOrderId(), "Payment declined by processor", Instant.now());
            kafkaTemplate.send("payments", orderId, failed);
        }
    }
}
