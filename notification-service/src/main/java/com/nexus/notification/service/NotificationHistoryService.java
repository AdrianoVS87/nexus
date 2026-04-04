package com.nexus.notification.service;

import com.nexus.notification.domain.Notification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class NotificationHistoryService {

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<Notification>> history = new ConcurrentHashMap<>();

    public void store(UUID orderId, UUID userId, String type, String channel, String message) {
        var notification = new Notification(
                UUID.randomUUID(),
                orderId,
                userId,
                type,
                channel,
                message,
                Instant.now()
        );
        history.computeIfAbsent(orderId, k -> new CopyOnWriteArrayList<>()).add(notification);
    }

    public List<Notification> getByOrderId(UUID orderId) {
        return history.getOrDefault(orderId, new CopyOnWriteArrayList<>());
    }
}
