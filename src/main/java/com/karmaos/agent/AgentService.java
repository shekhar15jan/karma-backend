package com.karmaos.agent;

import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AgentService {

    private final AgentRepository agentRepository;

    public AgentService(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    public List<AgentResponse> list() {
        return agentRepository.findByStatus("ACTIVE")
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public AgentResponse getById(UUID id) {
        Agent agent = agentRepository.findByIdAndStatus(id, "ACTIVE")
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Agent not found: " + id));
        return toResponse(agent);
    }

    @Transactional
    public AgentResponse create(AgentRequest request) {
        Agent agent = new Agent();
        agent.setName(request.getName());
        agent.setDescription(request.getDescription());
        agent.setCategory(request.getCategory());
        agent.setIcon(request.getIcon());
        agent.setDefaultPromptId(request.getDefaultPromptId());
        agent.setDefaultProviderId(request.getDefaultProviderId());
        agent = agentRepository.save(agent);
        return toResponse(agent);
    }

    @Transactional
    public AgentResponse update(UUID id, AgentRequest request) {
        Agent agent = agentRepository.findByIdAndStatus(id, "ACTIVE")
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Agent not found: " + id));
        agent.setName(request.getName());
        agent.setDescription(request.getDescription());
        agent.setCategory(request.getCategory());
        agent.setIcon(request.getIcon());
        agent.setDefaultPromptId(request.getDefaultPromptId());
        agent.setDefaultProviderId(request.getDefaultProviderId());
        agent = agentRepository.save(agent);
        return toResponse(agent);
    }

    @Transactional
    public void delete(UUID id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Agent not found: " + id));
        agent.softDelete();
        agentRepository.save(agent);
    }

    private AgentResponse toResponse(Agent a) {
        return AgentResponse.builder()
                .id(a.getId())
                .name(a.getName())
                .description(a.getDescription())
                .category(a.getCategory())
                .icon(a.getIcon())
                .status(a.getStatus())
                .defaultPromptId(a.getDefaultPromptId())
                .defaultProviderId(a.getDefaultProviderId())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
