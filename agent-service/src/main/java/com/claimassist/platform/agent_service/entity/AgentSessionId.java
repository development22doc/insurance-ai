package com.claimassist.platform.agent_service.entity;

import lombok.*;
import lombok.EqualsAndHashCode;
import java.io.Serializable;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
@Getter
@Setter
public class AgentSessionId implements Serializable {
    Long claimId;
    Long userId;
}
