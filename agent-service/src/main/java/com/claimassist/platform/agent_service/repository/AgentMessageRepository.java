package com.claimassist.platform.agent_service.repository;

import com.claimassist.platform.agent_service.entity.AgentMessage;
import com.claimassist.platform.agent_service.entity.AgentSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AgentMessageRepository extends JpaRepository<AgentMessage, Long> {

    // JOIN FETCH to avoid the classic N+1 (1 query for messages + N for their
    // events) - same fix as ChatMessageRepository in the Lovable clone.
    @Query("""
        SELECT DISTINCT m FROM AgentMessage m
        LEFT JOIN FETCH m.events e
        WHERE m.agentSession = :agentSession
        ORDER BY m.createdAt ASC, e.sequenceOrder ASC
        """)
    List<AgentMessage> findByAgentSessionWithEvents(@Param("agentSession") AgentSession agentSession);
}
