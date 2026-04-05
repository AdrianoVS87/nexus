package com.nexus.notification.repository;

import com.nexus.notification.domain.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
    List<NotificationEntity> findByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
