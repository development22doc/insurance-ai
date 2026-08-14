package com.claimassist.platform.claims_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * Permanent audit trail - never deleted, never overwritten. This is what
 * makes an AI-agent-proposed claim action defensible in an audit: exactly
 * what changed, who/what proposed it ("AGENT:<sagaId>" for agent-originated
 * changes, a userId for human ones), and when.
 */
@Entity
@Table(name = "claim_status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClaimStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false)
    Long claimId;

    @Column(nullable = false)
    String fromStatus;

    @Column(nullable = false)
    String toStatus;

    /** A userId as a string, OR "AGENT:<sagaId>" for an AI-agent-originated change. */
    @Column(nullable = false)
    String changedBy;

    String note;

    @Lob
    @Column(columnDefinition = "text")
    String metadata;

    @Builder.Default
    Instant changedAt = Instant.now();
}
