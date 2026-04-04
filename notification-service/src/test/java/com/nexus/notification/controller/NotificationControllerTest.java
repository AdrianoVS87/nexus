package com.nexus.notification.controller;

import com.nexus.notification.domain.Notification;
import com.nexus.notification.service.NotificationHistoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationHistoryService historyService;

    @Test
    @DisplayName("GET /api/v1/notifications/{orderId} returns 200 with notification list")
    void getNotifications_returns200() throws Exception {
        var orderId = UUID.randomUUID();
        var notification = new Notification(
                UUID.randomUUID(),
                orderId,
                UUID.randomUUID(),
                "ORDER_CONFIRMED",
                "EMAIL",
                "Order confirmed",
                Instant.parse("2026-04-03T12:00:00Z")
        );

        given(historyService.getByOrderId(orderId)).willReturn(List.of(notification));

        mockMvc.perform(get("/api/v1/notifications/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type", is("ORDER_CONFIRMED")))
                .andExpect(jsonPath("$[0].channel", is("EMAIL")))
                .andExpect(jsonPath("$[0].orderId", is(orderId.toString())));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/{orderId} returns 200 with empty list when no notifications")
    void getNotifications_returns200Empty() throws Exception {
        var orderId = UUID.randomUUID();
        given(historyService.getByOrderId(orderId)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/notifications/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
