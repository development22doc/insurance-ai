package com.claimassist.platform.policy_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "payment_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false)
    Purchase purchase;

    @Column(nullable = false, unique = true, length = 128)
    String providerEventId;

    @Column(nullable = false, length = 64)
    String eventType;

    @Column(nullable = false, length = 32)
    String eventStatus;

    @Lob
    @Column(nullable = false)
    String payload;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    Instant createdAt;

    @Column(nullable = false)
    Instant processedAt;
}
