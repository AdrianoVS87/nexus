package com.nexus.payment.service;

import com.nexus.payment.domain.entity.Payment;
import com.nexus.payment.domain.entity.Payment.PaymentStatus;
import com.nexus.common.event.PaymentCompleted;
import com.nexus.common.event.PaymentFailed;
import com.nexus.common.event.PaymentRefundRequested;
import com.nexus.common.event.PaymentRequested;
import com.nexus.payment.repository.PaymentRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private PaymentService paymentService;

    @Captor
    private ArgumentCaptor<Payment> paymentCaptor;

    @Captor
    private ArgumentCaptor<Object> eventCaptor;

    private UUID orderId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        ReflectionTestUtils.setField(paymentService, "successRate", 1.0);
    }

    private void stubKafkaSend() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());
    }

    private Payment.PaymentBuilder basePayment() {
        return Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .userId(UUID.randomUUID())
                .idempotencyKey(UUID.randomUUID().toString())
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .createdAt(Instant.now())
                .updatedAt(Instant.now());
    }

    private void stubSaveWithDefaults() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
                p.setCreatedAt(Instant.now());
                p.setUpdatedAt(Instant.now());
                if (p.getStatus() == null) p.setStatus(PaymentStatus.PENDING);
                if (p.getCurrency() == null) p.setCurrency("USD");
            }
            return p;
        });
    }

    @Test
    void handlePaymentRequested_withNewPayment_processesSuccessfully() {
        stubKafkaSend();
        var event = new PaymentRequested(orderId, UUID.randomUUID(), new BigDecimal("50.00"), "USD", UUID.randomUUID().toString(), Instant.now());
        var record = new ConsumerRecord<String, Object>("payments", 0, 0, orderId.toString(), event);

        when(paymentRepository.findByIdempotencyKey(event.idempotencyKey())).thenReturn(Optional.empty());
        stubSaveWithDefaults();

        paymentService.handlePaymentEvents(record);

        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(paymentCaptor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void handlePaymentRequested_withDuplicateIdempotencyKey_skipsProcessing() {
        stubKafkaSend();
        String idempotencyKey = UUID.randomUUID().toString();
        var event = new PaymentRequested(orderId, UUID.randomUUID(), new BigDecimal("50.00"), "USD", idempotencyKey, Instant.now());
        var record = new ConsumerRecord<String, Object>("payments", 0, 0, orderId.toString(), event);

        Payment existingPayment = basePayment()
                .idempotencyKey(idempotencyKey)
                .status(PaymentStatus.COMPLETED)
                .build();

        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingPayment));

        paymentService.handlePaymentEvents(record);

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(kafkaTemplate).send(eq("payments"), eq(orderId.toString()), any(PaymentCompleted.class));
    }

    @Test
    void handlePaymentRequested_publishesCompletedOnSuccess() {
        stubKafkaSend();
        ReflectionTestUtils.setField(paymentService, "successRate", 1.0);

        var event = new PaymentRequested(orderId, UUID.randomUUID(), new BigDecimal("75.00"), "USD", UUID.randomUUID().toString(), Instant.now());
        var record = new ConsumerRecord<String, Object>("payments", 0, 0, orderId.toString(), event);

        when(paymentRepository.findByIdempotencyKey(event.idempotencyKey())).thenReturn(Optional.empty());
        stubSaveWithDefaults();

        paymentService.handlePaymentEvents(record);

        verify(kafkaTemplate).send(eq("payments"), eq(orderId.toString()), eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(PaymentCompleted.class);
        PaymentCompleted completed = (PaymentCompleted) eventCaptor.getValue();
        assertThat(completed.orderId()).isEqualTo(orderId);
        assertThat(completed.amount()).isEqualByComparingTo(new BigDecimal("75.00"));
    }

    @Test
    void handlePaymentRequested_publishesFailedOnFailure() {
        stubKafkaSend();
        ReflectionTestUtils.setField(paymentService, "successRate", 0.0);

        var event = new PaymentRequested(orderId, UUID.randomUUID(), new BigDecimal("75.00"), "USD", UUID.randomUUID().toString(), Instant.now());
        var record = new ConsumerRecord<String, Object>("payments", 0, 0, orderId.toString(), event);

        when(paymentRepository.findByIdempotencyKey(event.idempotencyKey())).thenReturn(Optional.empty());
        stubSaveWithDefaults();

        paymentService.handlePaymentEvents(record);

        verify(kafkaTemplate).send(eq("payments"), eq(orderId.toString()), eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(PaymentFailed.class);

        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void handlePaymentRequested_withInvalidAmount_publishesFailed() {
        stubKafkaSend();
        var event = new PaymentRequested(orderId, UUID.randomUUID(), new BigDecimal("-10.00"), "USD", UUID.randomUUID().toString(), Instant.now());
        var record = new ConsumerRecord<String, Object>("payments", 0, 0, orderId.toString(), event);

        paymentService.handlePaymentEvents(record);

        verify(kafkaTemplate).send(eq("payments"), eq(orderId.toString()), eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(PaymentFailed.class);
        PaymentFailed failed = (PaymentFailed) eventCaptor.getValue();
        assertThat(failed.reason()).contains("Invalid payment amount");
        assertThat(failed.userId()).isNotNull();

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void handleRefundRequested_refundsCompletedPayment() {
        var event = new PaymentRefundRequested(orderId, UUID.randomUUID(), java.math.BigDecimal.TEN, "Customer requested", Instant.now());
        var record = new ConsumerRecord<String, Object>("payments", 0, 0, orderId.toString(), event);

        Payment payment = basePayment().status(PaymentStatus.COMPLETED).build();

        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        paymentService.handlePaymentEvents(record);

        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void handleRefundRequested_ignoresAlreadyRefunded() {
        var event = new PaymentRefundRequested(orderId, UUID.randomUUID(), java.math.BigDecimal.TEN, "Customer requested", Instant.now());
        var record = new ConsumerRecord<String, Object>("payments", 0, 0, orderId.toString(), event);

        Payment payment = basePayment().status(PaymentStatus.REFUNDED).build();

        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));

        paymentService.handlePaymentEvents(record);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void handleRefundRequested_rejectsNonCompletedPayment() {
        var event = new PaymentRefundRequested(orderId, UUID.randomUUID(), java.math.BigDecimal.TEN, "Customer requested", Instant.now());
        var record = new ConsumerRecord<String, Object>("payments", 0, 0, orderId.toString(), event);

        Payment payment = basePayment().status(PaymentStatus.FAILED).build();

        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));

        paymentService.handlePaymentEvents(record);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void handleRefundRequested_handlesNonExistentPayment() {
        var event = new PaymentRefundRequested(orderId, UUID.randomUUID(), java.math.BigDecimal.TEN, "Customer requested", Instant.now());
        var record = new ConsumerRecord<String, Object>("payments", 0, 0, orderId.toString(), event);

        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        paymentService.handlePaymentEvents(record);

        verify(paymentRepository, never()).save(any());
    }
}
