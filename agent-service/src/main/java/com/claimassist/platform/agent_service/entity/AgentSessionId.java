package com.claimassist.platform.agent_service.entity;

import lombok.*;
import java.io.Serializable;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
@Getter
@Setter
public class AgentSessionId implements Serializable {
    Long claimId;
    Long userId;
}
