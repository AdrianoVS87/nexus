package com.nexus.inventory.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_reservations")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservation {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Setter
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = ReservationStatus.RESERVED;
    }

    /**
     * Transitions this reservation from RESERVED to RELEASED.
     *
     * @throws IllegalStateException if current status is not RESERVED
     */
    public void release() {
        if (status != ReservationStatus.RESERVED) {
            throw new IllegalStateException(
                    "Cannot release reservation %s: current status is %s, expected RESERVED".formatted(id, status));
        }
        status = ReservationStatus.RELEASED;
    }

    /**
     * Transitions this reservation from RESERVED to CONSUMED.
     *
     * @throws IllegalStateException if current status is not RESERVED
     */
    public void consume() {
        if (status != ReservationStatus.RESERVED) {
            throw new IllegalStateException(
                    "Cannot consume reservation %s: current status is %s, expected RESERVED".formatted(id, status));
        }
        status = ReservationStatus.CONSUMED;
    }

    public enum ReservationStatus {
        RESERVED, RELEASED, CONSUMED
    }
}
