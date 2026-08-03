package com.claimassist.platform.agent_service.mapper;

import com.claimassist.platform.agent_service.dto.agent.AgentMessageResponse;
import com.claimassist.platform.agent_service.entity.AgentMessage;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AgentMapper {
    // MapStruct auto-generates the nested AgentEvent -> AgentEventResponse
    // mapping (field names match: id, type, status, sequenceOrder, content,
    // sagaId, proposedStatus) - no explicit method needed, same pattern as
    // ChatMapper in the Lovable clone.
    List<AgentMessageResponse> fromListOfAgentMessage(List<AgentMessage> messages);
}
