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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final String TOPIC_PAYMENTS = "payments";
    private static final String DECLINE_REASON = "Payment declined by processor";

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${payment.success-rate:0.8}")
    private double successRate;

    @KafkaListener(topics = TOPIC_PAYMENTS, groupId = "payment-service")
    @Transactional
    public void handlePaymentEvents(org.apache.kafka.clients.consumer.ConsumerRecord<String, Object> record) {
        Object event = record.value();
        log.info("Kafka event received: type={}", event.getClass().getSimpleName());
        if (event instanceof PaymentRequested pr) {
            handlePaymentRequested(pr);
        } else if (event instanceof PaymentRefundRequested prr) {
            handleRefundRequested(prr);
        }
    }

    private void handlePaymentRequested(PaymentRequested event) {
        log.info("Received PaymentRequested: orderId={}, idempotencyKey={}", event.orderId(), event.idempotencyKey());

        if (event.amount() == null || event.amount().compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Invalid payment amount: orderId={}, amount={}", event.orderId(), event.amount());
            var failed = new PaymentFailed(event.orderId(), "Invalid payment amount", Instant.now());
            sendEvent(event.orderId().toString(), failed, "PaymentFailed");
            return;
        }

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

        boolean success = ThreadLocalRandom.current().nextDouble() < successRate;

        if (success) {
            payment.setStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(payment);

            var completed = new PaymentCompleted(payment.getId(), event.orderId(), event.amount(), Instant.now());
            sendEvent(event.orderId().toString(), completed, "PaymentCompleted");
            log.info("Payment completed: paymentId={}, orderId={}", payment.getId(), event.orderId());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            var failed = new PaymentFailed(event.orderId(), DECLINE_REASON, Instant.now());
            sendEvent(event.orderId().toString(), failed, "PaymentFailed");
            log.info("Payment failed: orderId={}", event.orderId());
        }
    }

    private void handleRefundRequested(PaymentRefundRequested event) {
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
            sendEvent(orderId, completed, "PaymentCompleted");
        } else if (payment.getStatus() == PaymentStatus.FAILED) {
            var failed = new PaymentFailed(payment.getOrderId(), DECLINE_REASON, Instant.now());
            sendEvent(orderId, failed, "PaymentFailed");
        }
    }

    private void sendEvent(String key, Object event, String eventType) {
        kafkaTemplate.send(TOPIC_PAYMENTS, key, event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish {} for key={}: {}", eventType, key, ex.getMessage(), ex);
            } else {
                log.info("Published {}: key={}, offset={}", eventType, key,
                        result.getRecordMetadata().offset());
            }
        });
    }
}
