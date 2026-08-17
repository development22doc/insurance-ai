package com.claimassist.platform.agent_service.ai.tool;

/**
 * Classifies an agent tool's operational risk. READ tools only fetch data and
 * never change state; WRITE tools feed a business workflow (proposal/saga) and
 * therefore carry a higher operational risk profile.
 * <p>
 * This is an application-side classification for the {@link ToolRegistry}; it
 * does not change what the LLM sees (Spring AI's own tool schema remains the
 * source of truth for tool selection).
 */
public enum ToolRiskLevel {
    READ,
    WRITE
}