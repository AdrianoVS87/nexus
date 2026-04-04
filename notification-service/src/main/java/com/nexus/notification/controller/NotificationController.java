package com.nexus.notification.controller;

import com.nexus.notification.domain.Notification;
import com.nexus.notification.service.NotificationHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationHistoryService historyService;

    @GetMapping("/{orderId}")
    public ResponseEntity<List<Notification>> getNotificationsByOrderId(@PathVariable UUID orderId) {
        List<Notification> notifications = historyService.getByOrderId(orderId);
        return ResponseEntity.ok(notifications);
    }
}
