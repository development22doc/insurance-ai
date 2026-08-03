package com.claimassist.platform.agent_service.entity;

import com.claimassist.platform.common_lib.enums.MessageRole;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "agent_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AgentMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne
    @JoinColumns({
            @JoinColumn(name = "claim_id", referencedColumnName = "claim_id"),
            @JoinColumn(name = "user_id", referencedColumnName = "user_id")
    })
    AgentSession agentSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    MessageRole role;

    @Lob
    @Column(columnDefinition = "text")
    String content;

    @Builder.Default
    Integer tokensUsed = 0;

    @OneToMany(mappedBy = "agentMessage", cascade = CascadeType.ALL)
    @Builder.Default
    List<AgentEvent> events = new ArrayList<>();

    @Builder.Default
    Instant createdAt = Instant.now();
}
