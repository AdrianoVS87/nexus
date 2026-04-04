package com.nexus.payment.controller;

import com.nexus.payment.domain.entity.Payment;
import com.nexus.payment.domain.entity.Payment.PaymentStatus;
import com.nexus.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(
        partitions = 1,
        topics = {"payments"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"}
)
@ActiveProfiles("test")
class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
    }

    @Test
    void getPaymentByOrderId_returns200() throws Exception {
        UUID orderId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .idempotencyKey(UUID.randomUUID().toString())
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        paymentRepository.save(payment);

        mockMvc.perform(get("/api/v1/payments/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.amount").value(99.99))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void getPaymentByOrderId_returns404ForNonExistent() throws Exception {
        mockMvc.perform(get("/api/v1/payments/{orderId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Payment Not Found"));
    }
}
