package com.claimassist.platform.agent_service.repository;

import com.claimassist.platform.agent_service.entity.AgentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentEventRepository extends JpaRepository<AgentEvent, Long> {
    Optional<AgentEvent> findBySagaId(String sagaId);
}
