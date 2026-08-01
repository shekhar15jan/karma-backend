package com.karmaos.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmaos.agent.AgentRepository;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import com.karmaos.junction.FlowAgent;
import com.karmaos.junction.FlowAgentId;
import com.karmaos.junction.FlowAgentRepository;
import com.karmaos.junction.MissionFlow;
import com.karmaos.junction.MissionFlowRepository;
import com.karmaos.mission.MissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FlowService {

    private final FlowRepository flowRepository;
    private final FlowAgentRepository flowAgentRepository;
    private final AgentRepository agentRepository;
    private final MissionFlowRepository missionFlowRepository;
    private final MissionRepository missionRepository;
    private final ObjectMapper objectMapper;

    public FlowService(FlowRepository flowRepository,
                       FlowAgentRepository flowAgentRepository,
                       AgentRepository agentRepository,
                       MissionFlowRepository missionFlowRepository,
                       MissionRepository missionRepository,
                       ObjectMapper objectMapper) {
        this.flowRepository = flowRepository;
        this.flowAgentRepository = flowAgentRepository;
        this.agentRepository = agentRepository;
        this.missionFlowRepository = missionFlowRepository;
        this.missionRepository = missionRepository;
        this.objectMapper = objectMapper;
    }

    public List<FlowResponse> list() {
        return flowRepository.findByStatus("ACTIVE")
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public FlowResponse getById(UUID id) {
        Flow flow = flowRepository.findByIdAndStatus(id, "ACTIVE")
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Flow not found: " + id));
        return toResponse(flow);
    }

    @Transactional
    public FlowResponse create(FlowRequest request) {
        String name = normalizeName(request.getName());
        ensureUniqueName(name, null);
        Flow flow = new Flow();
        flow.setName(name);
        flow.setDescription(request.getDescription());
        flow.setCategory(normalizeCategory(request.getCategory()));
        flow.setExecutionOrder(request.getExecutionOrder());
        flow.setParameters(serializeParameters(request.getParameters()));
        flow.setEnabled(true);
        flow = flowRepository.save(flow);
        linkAgents(flow.getId(), request.getAgentIds());
        return toResponse(flow);
    }

    @Transactional
    public FlowResponse update(UUID id, FlowRequest request) {
        Flow flow = flowRepository.findByIdAndStatus(id, "ACTIVE")
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Flow not found: " + id));
        String name = normalizeName(request.getName());
        ensureUniqueName(name, id);
        flow.setName(name);
        flow.setDescription(request.getDescription());
        flow.setCategory(normalizeCategory(request.getCategory()));
        flow.setExecutionOrder(request.getExecutionOrder());
        flow.setParameters(serializeParameters(request.getParameters()));
        flow = flowRepository.save(flow);
        replaceAgents(flow.getId(), request.getAgentIds());
        return toResponse(flow);
    }

    @Transactional
    public void delete(UUID id) {
        Flow flow = flowRepository.findByIdAndStatus(id, "ACTIVE")
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Flow not found: " + id));

        List<MissionFlow> references = missionFlowRepository.findByIdFlowId(id);
        for (MissionFlow reference : references) {
            missionRepository.findById(reference.getId().getMissionId()).ifPresent(mission -> {
                String status = mission.getStatus() != null ? mission.getStatus() : "";
                if (!"CANCELLED".equalsIgnoreCase(status) && !"DELETED".equalsIgnoreCase(status)) {
                    throw new ApiException(ErrorCode.CONFLICT,
                            "Flow '" + flow.getName() + "' is referenced by mission '" + mission.getName()
                                    + "' (" + status + "); delete or cancel the mission first");
                }
            });
        }
        missionFlowRepository.deleteAll(references);
        flow.softDelete();
        flowRepository.save(flow);
    }

    private void linkAgents(UUID flowId, List<UUID> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return;
        }
        Set<UUID> seen = new LinkedHashSet<>();
        int order = 0;
        for (UUID agentId : agentIds) {
            if (agentId == null || !seen.add(agentId)) {
                continue;
            }
            agentRepository.findByIdAndStatus(agentId, "ACTIVE")
                    .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST,
                            "Agent not found or inactive: " + agentId));
            FlowAgent fa = new FlowAgent();
            fa.setId(new FlowAgentId(flowId, agentId));
            fa.setStepOrder(order++);
            flowAgentRepository.save(fa);
        }
    }

    private void replaceAgents(UUID flowId, List<UUID> agentIds) {
        flowAgentRepository.deleteAll(flowAgentRepository.findByIdFlowId(flowId));
        linkAgents(flowId, agentIds);
    }

    private String normalizeName(String name) {
        String normalized = name == null ? null : name.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Flow name is required");
        }
        return normalized;
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        return category.trim().toUpperCase();
    }

    private void ensureUniqueName(String name, UUID selfId) {
        flowRepository.findByNameAndStatus(name, "ACTIVE")
                .filter(flow -> !flow.getId().equals(selfId))
                .ifPresent(flow -> {
                    throw new ApiException(ErrorCode.DUPLICATE_NAME, "Flow name already exists: " + name);
                });
    }

    private String serializeParameters(Object parameters) {
        if (parameters == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid flow parameters");
        }
    }

    private FlowResponse toResponse(Flow f) {
        List<UUID> agentIds = flowAgentRepository.findByIdFlowIdOrderByStepOrderAsc(f.getId()).stream()
                .map(fa -> fa.getId().getAgentId())
                .collect(Collectors.toList());
        return FlowResponse.builder()
                .id(f.getId())
                .name(f.getName())
                .description(f.getDescription())
                .category(f.getCategory())
                .executionOrder(f.getExecutionOrder())
                .enabled(f.isEnabled())
                .status(f.getStatus())
                .agentIds(agentIds)
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .build();
    }
}
