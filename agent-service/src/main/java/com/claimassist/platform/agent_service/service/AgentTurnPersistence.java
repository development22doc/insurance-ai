package com.claimassist.platform.agent_service.service;

import com.claimassist.platform.agent_service.ai.tool.ToolExecutionMetadata;
import com.claimassist.platform.agent_service.entity.AgentSession;
import com.claimassist.platform.agent_service.llm.InsuranceAgentTools.ProposedUpdate;

import java.util.List;

/**
 * Contract for persisting the outcome of one completed agent turn. Extracted
 * to an interface so the command service depends on an abstraction (testable,
 * DIP) rather than a concrete class.
 */
public interface AgentTurnPersistence {

    void finalizeTurn(String userMessage, AgentSession session, String fullText, long durationSeconds,
                      Object usage, Long userId, List<ProposedUpdate> proposedUpdates,
                      List<ToolExecutionMetadata> toolExecutions);
}