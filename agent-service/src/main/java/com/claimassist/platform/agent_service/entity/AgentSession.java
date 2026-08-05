package com.claimassist.platform.agent_service.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * One conversation per (claim, user) - same composite-key shape as
 * ChatSession in the Lovable clone. A null claimId (general "what does my
 * policy cover" Q&A not tied to a specific claim) is handled by the command
 * service using a sentinel; kept simple here as claim-scoped only.
 */
@Entity
@Table(name = "agent_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AgentSession {

    @EmbeddedId
    @AttributeOverrides({
            @AttributeOverride(name = "claimId", column = @Column(name = "claim_id")),
            @AttributeOverride(name = "userId", column = @Column(name = "user_id"))
    })
    AgentSessionId id;
}
