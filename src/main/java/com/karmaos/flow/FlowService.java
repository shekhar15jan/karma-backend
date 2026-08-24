package com.karmaos.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.karmaos.agent.AgentRepository;
import com.karmaos.artifact.Artifact;
import com.karmaos.artifact.ArtifactRepository;
import com.karmaos.artifact.ArtifactResponse;
import com.karmaos.common.ApiException;
import com.karmaos.common.ErrorCode;
import com.karmaos.junction.FlowAgent;
import com.karmaos.junction.FlowAgentId;
import com.karmaos.junction.FlowAgentRepository;
import com.karmaos.junction.MissionFlow;
import com.karmaos.junction.MissionFlowRepository;
import com.karmaos.mission.MissionRepository;
import com.karmaos.workflow.DesignPlan;
import com.karmaos.workflow.WorkflowRun;
import com.karmaos.workflow.WorkflowRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private final WorkflowRunRepository workflowRunRepository;
    private final ArtifactRepository artifactRepository;
    private final ObjectMapper objectMapper;

    public FlowService(FlowRepository flowRepository,
                       FlowAgentRepository flowAgentRepository,
                       AgentRepository agentRepository,
                       MissionFlowRepository missionFlowRepository,
                       MissionRepository missionRepository,
                       WorkflowRunRepository workflowRunRepository,
                       ArtifactRepository artifactRepository,
                       ObjectMapper objectMapper) {
        this.flowRepository = flowRepository;
        this.flowAgentRepository = flowAgentRepository;
        this.agentRepository = agentRepository;
        this.missionFlowRepository = missionFlowRepository;
        this.missionRepository = missionRepository;
        this.workflowRunRepository = workflowRunRepository;
        this.artifactRepository = artifactRepository;
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

    public FlowDetailResponse getDetails(UUID id) {
        Flow flow = flowRepository.findByIdAndStatus(id, "ACTIVE")
                .orElseThrow(() -> ErrorCode.NOT_FOUND.exception("Flow not found: " + id));

        List<WorkflowRun> runs = workflowRunRepository.findByFlowIdOrderByStartedAtDesc(id);

        List<UUID> missionIds = runs.stream()
                .map(WorkflowRun::getMissionId)
                .filter(m -> m != null)
                .collect(Collectors.toList());

        List<ArtifactResponse> artifacts = missionIds.isEmpty()
                ? Collections.emptyList()
                : artifactRepository.findByMissionIdInOrderByCreatedAtDesc(missionIds).stream()
                        .map(this::toArtifactResponse)
                        .collect(Collectors.toList());

        List<FlowRunSummary> recentRuns = runs.stream()
                .map(r -> FlowRunSummary.builder()
                        .id(r.getId())
                        .executionId(r.getExecutionId())
                        .missionId(r.getMissionId())
                        .status(r.getStatus())
                        .startedAt(r.getStartedAt())
                        .completedAt(r.getCompletedAt())
                        .duration(r.getDuration())
                        .build())
                .collect(Collectors.toList());

        List<UUID> agentIds = flowAgentRepository.findByIdFlowIdOrderByStepOrderAsc(flow.getId()).stream()
                .map(fa -> fa.getId().getAgentId())
                .collect(Collectors.toList());

        return FlowDetailResponse.builder()
                .id(flow.getId())
                .name(flow.getName())
                .description(flow.getDescription())
                .category(flow.getCategory())
                .executionOrder(flow.getExecutionOrder())
                .enabled(flow.isEnabled())
                .status(flow.getStatus())
                .isSystem(flow.isSystem())
                .agentIds(agentIds)
                .design(extractDesign(flow.getParameters(), agentIds))
                .runCount(runs.size())
                .recentRuns(recentRuns)
                .artifacts(artifacts)
                .createdAt(flow.getCreatedAt())
                .updatedAt(flow.getUpdatedAt())
                .build();
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

        if (flow.isSystem()) {
            throw ErrorCode.BAD_REQUEST.exception("System flow cannot be deleted");
        }

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

    @Transactional
    public FlowResponse upsertDesignerFlow(String name, String description, String category,
                                           List<Map<String, Object>> nodes, List<Map<String, String>> edges) {
        String normalizedName = normalizeName(name);
        List<UUID> orderedAgentIds = topoAgentIds(nodes, edges);
        Object designParams = buildDesignParameters(nodes, edges);

        Flow existing = flowRepository.findByNameAndStatus(normalizedName, "ACTIVE").orElse(null);
        if (existing != null) {
            if (existing.isSystem()) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                    "System flow cannot be overwritten; choose a different name");
            }
            existing.setName(normalizedName);
            existing.setDescription(description);
            existing.setCategory(normalizeCategory(category != null && !category.isBlank() ? category : "WORKFLOW"));
            existing.setParameters(serializeParameters(designParams));
            existing = flowRepository.save(existing);
            replaceAgents(existing.getId(), orderedAgentIds);
            return toResponse(existing);
        }

        Flow flow = new Flow();
        flow.setName(normalizedName);
        flow.setDescription(description);
        flow.setCategory(normalizeCategory(category != null && !category.isBlank() ? category : "WORKFLOW"));
        flow.setExecutionOrder(0);
        flow.setParameters(serializeParameters(designParams));
        flow.setEnabled(true);
        flow = flowRepository.save(flow);
        linkAgents(flow.getId(), orderedAgentIds);
        return toResponse(flow);
    }

    private List<UUID> topoAgentIds(List<Map<String, Object>> nodes, List<Map<String, String>> edges) {
        List<UUID> agentIds = new ArrayList<>();
        for (DesignPlan.Node node : DesignPlan.resolve(nodes, edges)) {
            if (node.agentId != null) {
                agentIds.add(node.agentId);
            }
        }
        return agentIds;
    }

    private Map<String, Object> buildDesignParameters(List<Map<String, Object>> nodes,
                                                       List<Map<String, String>> edges) {
        Map<String, Object> design = new HashMap<>();
        design.put("nodes", nodes == null ? new ArrayList<Object>() : nodes);
        design.put("edges", edges == null ? new ArrayList<Object>() : edges);
        Map<String, Object> params = new HashMap<>();
        params.put(DesignPlan.KEY_DESIGN, design);
        return params;
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractDesign(String parameters, List<UUID> agentIds) {
        if (parameters == null || parameters.isBlank()) {
            return synthesizeDesign(agentIds);
        }
        try {
            Map<String, Object> params = objectMapper.readValue(parameters, new TypeReference<Map<String, Object>>() {});
            Object design = params.get(DesignPlan.KEY_DESIGN);
            if (design instanceof Map) {
                Map<String, Object> d = (Map<String, Object>) design;
                List<?> nodes = (List<?>) d.get("nodes");
                if (nodes == null || nodes.isEmpty()) {
                    return synthesizeDesign(agentIds);
                }
                return d;
            }
            return synthesizeDesign(agentIds);
        } catch (Exception e) {
            return synthesizeDesign(agentIds);
        }
    }

    private Map<String, Object> synthesizeDesign(List<UUID> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        int x = 280;
        for (int i = 0; i < agentIds.size(); i++) {
            UUID agentId = agentIds.get(i);
            String agentName = agentRepository.findById(agentId)
                    .map(com.karmaos.agent.Agent::getName)
                    .orElse("Agent");
            
            Map<String, Object> node = new HashMap<>();
            node.put("id", "node-" + i);
            node.put("agentId", agentId.toString());
            node.put("agentName", agentName);
            node.put("label", agentName);
            node.put("stepKind", "AGENT");
            node.put("status", "ready");
            node.put("x", x);
            node.put("y", 80);
            nodes.add(node);
            
            if (i > 0) {
                Map<String, Object> edge = new HashMap<>();
                edge.put("id", "edge-" + (i - 1));
                edge.put("source", "node-" + (i - 1));
                edge.put("target", "node-" + i);
                edge.put("sourcePort", "right");
                edge.put("targetPort", "left");
                edge.put("handoff", true);
                edges.add(edge);
            }
            
            x += 220;
        }
        Map<String, Object> design = new HashMap<>();
        design.put("nodes", nodes);
        design.put("edges", edges);
        return design;
    }

    private ArtifactResponse toArtifactResponse(Artifact a) {
        List<String> tags = Collections.emptyList();
        try {
            tags = objectMapper.readValue(a.getTags(), new TypeReference<List<String>>() {});
        } catch (Exception ignored) {
        }
        return ArtifactResponse.builder()
                .id(a.getId())
                .missionId(a.getMissionId())
                .executionStepId(a.getExecutionStepId())
                .name(a.getName())
                .artifactType(a.getArtifactType())
                .status(a.getStatus())
                .contentText(a.getContentText())
                .contentType(a.getContentType())
                .storageLocation(a.getStorageLocation())
                .reviewStatus(a.getReviewStatus())
                .publicationStatus(a.getPublicationStatus())
                .tags(tags)
                .currentVersion(a.getCurrentVersion())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
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
                .isSystem(f.isSystem())
                .agentIds(agentIds)
                .design(extractDesign(f.getParameters(), agentIds))
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .build();
    }
}
