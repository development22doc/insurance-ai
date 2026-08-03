package com.claimassist.platform.agent_service.entity;

import com.claimassist.platform.common_lib.enums.AgentEventStatus;
import com.claimassist.platform.common_lib.enums.AgentEventType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * One row per parsed chunk of the agent's turn - THOUGHT/MESSAGE/TOOL_LOG are
 * always created CONFIRMED; CLAIM_UPDATE_PROPOSED starts PENDING and is
 * flipped to CONFIRMED/FAILED by AgentSagaResponseHandler once claims-service
 * responds. This row IS the audit trail an insurance regulator would ask for:
 * "show me every action this AI proposed, whether it was accepted, and why."
 */
@Entity
@Table(name = "agent_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AgentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne
    @JoinColumn(name = "agent_message_id")
    AgentMessage agentMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    AgentEventType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    AgentEventStatus status;

    @Column(nullable = false)
    Integer sequenceOrder;

    @Lob
    @Column(columnDefinition = "text")
    String content;

    /** Only set for CLAIM_UPDATE_PROPOSED - correlates to the saga. */
    String sagaId;

    String proposedStatus;
}
