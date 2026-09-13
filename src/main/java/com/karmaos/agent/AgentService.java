package com.karmaos.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import com.karmaos.junction.AgentSkill;
import com.karmaos.junction.AgentSkillId;
import com.karmaos.junction.AgentSkillRepository;
import com.karmaos.mcp.McpServer;
import com.karmaos.mcp.McpServerRepository;
import com.karmaos.agent.AgentMcpServerRepository;
import com.karmaos.provider.Provider;
import com.karmaos.provider.ProviderRepository;
import com.karmaos.skill.Skill;
import com.karmaos.skill.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentRepository agentRepository;
    private final ProviderRepository providerRepository;
    private final AgentSkillRepository agentSkillRepository;
    private final SkillRepository skillRepository;
    private final McpServerRepository mcpServerRepository;
    private final AgentMcpServerRepository agentMcpServerRepository;
    private final ObjectMapper objectMapper;

    public List<AgentResponse> list() {
        return agentRepository.findByStatusNot("DELETED")
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public AgentResponse getById(UUID id) {
        Agent agent = findNonDeleted(id);
        return toResponse(agent);
    }

    @Transactional
    public AgentResponse create(AgentRequest request) {
        validateProviders(request);
        Agent agent = new Agent();
        agent.setName(request.getName());
        agent.setDescription(request.getDescription());
        agent.setCategory(request.getCategory());
        agent.setIcon(request.getIcon());
        agent.setDefaultPromptId(request.getDefaultPromptId());
        agent.setDefaultProviderId(request.getDefaultProviderId());
        agent.setFallbackProviderId(request.getFallbackProviderId());
        agent.setConfiguration(buildConfiguration(request));
        agent = agentRepository.save(agent);
        syncSkills(agent.getId(), request.getSkillIds());
        syncMcpServers(agent.getId(), request.getMcpServerIds());
        return toResponse(agent);
    }

    @Transactional
    public AgentResponse update(UUID id, AgentRequest request) {
        Agent agent = findNonDeleted(id);
        validateProviders(request);
        agent.setName(request.getName());
        agent.setDescription(request.getDescription());
        agent.setCategory(request.getCategory());
        agent.setIcon(request.getIcon());
        agent.setDefaultPromptId(request.getDefaultPromptId());
        agent.setDefaultProviderId(request.getDefaultProviderId());
        agent.setFallbackProviderId(request.getFallbackProviderId());
        agent.setConfiguration(buildConfiguration(request));
        agent = agentRepository.save(agent);
        syncSkills(agent.getId(), request.getSkillIds());
        syncMcpServers(agent.getId(), request.getMcpServerIds());
        return toResponse(agent);
    }

    @Transactional
    public void delete(UUID id) {
        Agent agent = findNonDeleted(id);
        if (agent.isSystem()) {
            throw ErrorCode.BAD_REQUEST.exception("System agent cannot be deleted");
        }
        agent.softDelete();
        agentRepository.save(agent);
    }

    @Transactional
    public void activate(UUID id) {
        Agent agent = findNonDeleted(id);
        agent.activate();
        agentRepository.save(agent);
    }

    @Transactional
    public void deactivate(UUID id) {
        Agent agent = findNonDeleted(id);
        agent.setStatus("INACTIVE");
        agentRepository.save(agent);
    }

    private Agent findNonDeleted(UUID id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Agent not found: " + id));
        if (agent.getDeletedAt() != null || "DELETED".equals(agent.getStatus())) {
            throw ErrorCode.NOT_FOUND.exception("Agent not found: " + id);
        }
        return agent;
    }

    /**
     * Merges model/temperature/memoryMode into the existing configuration JSONB,
     * preserving any other keys (e.g. temperature set by the editor).
     */
    private String buildConfiguration(AgentRequest request) {
        Map<String, Object> config = new LinkedHashMap<>();
        if (request.getConfiguration() instanceof Map<?, ?> existing) {
            for (Map.Entry<?, ?> e : existing.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    config.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        }
        if (request.getModel() != null) {
            config.put("model", request.getModel());
        }
        if (request.getTemperature() != null) {
            config.put("temperature", request.getTemperature());
        }
        if (request.getMemoryMode() != null) {
            config.put("memoryMode", request.getMemoryMode().toUpperCase());
        }
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void syncSkills(UUID agentId, List<UUID> skillIds) {
        if (skillIds == null) {
            return;
        }
        Set<UUID> target = new HashSet<>();
        if (!skillIds.isEmpty()) {
            for (UUID skillId : skillIds) {
                if (skillId == null) continue;
                skillRepository.findById(skillId).ifPresent(s -> {
                    if (s.getDeletedAt() == null && isLiveSkillStatus(s.getStatus())) {
                        target.add(skillId);
                    }
                });
            }
        }
        List<AgentSkill> existing = agentSkillRepository.findByIdAgentId(agentId);
        for (AgentSkill link : existing) {
            if (!target.contains(link.getId().getSkillId())) {
                agentSkillRepository.delete(link);
            }
        }
        Set<UUID> existingIds = existing.stream()
                .map(s -> s.getId().getSkillId())
                .collect(Collectors.toSet());
        for (UUID skillId : target) {
            if (!existingIds.contains(skillId)) {
                AgentSkill link = new AgentSkill();
                link.setId(new AgentSkillId(agentId, skillId));
                agentSkillRepository.save(link);
            }
        }
    }

    private void syncMcpServers(UUID agentId, List<UUID> mcpServerIds) {
        if (mcpServerIds == null) {
            return;
        }
        Set<UUID> target = new HashSet<>();
        if (!mcpServerIds.isEmpty()) {
            for (UUID mcpId : mcpServerIds) {
                if (mcpId == null) continue;
                mcpServerRepository.findById(mcpId).ifPresent(s -> {
                    if ("ACTIVE".equals(s.getStatus())) {
                        target.add(mcpId);
                    }
                });
            }
        }
        List<AgentMcpServer> existing = agentMcpServerRepository.findByIdAgentId(agentId);
        for (AgentMcpServer link : existing) {
            if (!target.contains(link.getId().getMcpServerId())) {
                agentMcpServerRepository.delete(link);
            }
        }
        Set<UUID> existingIds = existing.stream()
                .map(s -> s.getId().getMcpServerId())
                .collect(Collectors.toSet());
        for (UUID mcpId : target) {
            if (!existingIds.contains(mcpId)) {
                AgentMcpServer link = new AgentMcpServer();
                link.setId(new AgentMcpServerId(agentId, mcpId));
                agentMcpServerRepository.save(link);
            }
        }
    }

    private void validateProviders(AgentRequest request) {
        validateProvider(request.getDefaultProviderId());
        validateProvider(request.getFallbackProviderId());
    }

    private void validateProvider(UUID providerId) {
        if (providerId == null) return;
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Provider not found: " + providerId));
        if (provider.getDeletedAt() != null || "PUBLISHING".equalsIgnoreCase(provider.getProviderType())) {
            throw ErrorCode.NOT_FOUND.exception("Provider not available: " + providerId);
        }
    }

    private AgentResponse toResponse(Agent a) {
        Map<String, Object> config = parseConfiguration(a.getConfiguration());

        Provider provider = a.getDefaultProviderId() != null
                ? providerRepository.findById(a.getDefaultProviderId()).orElse(null)
                : null;

        List<AgentResponse.AgentSkillInfo> skills = agentSkillRepository.findByIdAgentId(a.getId()).stream()
                .map(as -> skillRepository.findById(as.getId().getSkillId()).orElse(null))
                .filter(s -> s != null && s.getDeletedAt() == null && isLiveSkillStatus(s.getStatus()))
                .map(s -> AgentResponse.AgentSkillInfo.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .build())
                .collect(Collectors.toList());

        List<AgentResponse.AgentMcpServerInfo> mcpServers = agentMcpServerRepository.findByIdAgentId(a.getId()).stream()
                .map(am -> mcpServerRepository.findById(am.getId().getMcpServerId()).orElse(null))
                .filter(s -> s != null && "ACTIVE".equals(s.getStatus()))
                .map(s -> AgentResponse.AgentMcpServerInfo.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .transportType(s.getTransportType())
                        .build())
                .collect(Collectors.toList());

        return AgentResponse.builder()
                .id(a.getId())
                .name(a.getName())
                .description(a.getDescription())
                .category(a.getCategory())
                .icon(a.getIcon())
                .status(a.getStatus())
                .defaultPromptId(a.getDefaultPromptId())
                .defaultProviderId(a.getDefaultProviderId())
                .fallbackProviderId(a.getFallbackProviderId())
                .providerName(provider != null ? provider.getName() : null)
                .providerType(provider != null ? provider.getProviderType() : null)
                .model(asString(config.get("model")))
                .temperature(config.get("temperature") instanceof Number n ? n.doubleValue() : null)
                .memoryMode(asString(config.get("memoryMode")))
                .isSystem(a.isSystem())
                .skills(skills)
                .mcpServers(mcpServers)
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    private Map<String, Object> parseConfiguration(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean isLiveSkillStatus(String status) {
        return status != null && ("ACTIVE".equals(status) || "INSTALLED".equals(status));
    }
}
