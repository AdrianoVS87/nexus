package com.nexus.notification.service;

import com.nexus.notification.domain.Notification;
import com.nexus.notification.domain.entity.NotificationEntity;
import com.nexus.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationHistoryService {

    private final NotificationRepository repository;

    @Transactional
    public void store(UUID orderId, UUID userId, String type, String channel, String message) {
        repository.save(NotificationEntity.builder()
                .orderId(orderId)
                .userId(userId)
                .type(type)
                .channel(channel)
                .message(message)
                .build());
    }

    @Transactional(readOnly = true)
    public List<Notification> getByOrderId(UUID orderId) {
        return repository.findByOrderIdOrderByCreatedAtDesc(orderId)
                .stream()
                .map(entity -> new Notification(
                        entity.getId(),
                        entity.getOrderId(),
                        entity.getUserId(),
                        entity.getType(),
                        entity.getChannel(),
                        entity.getMessage(),
                        entity.getCreatedAt()))
                .toList();
    }
}
