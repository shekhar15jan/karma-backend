package com.karmaos.search;

import com.karmaos.mission.MissionRepository;
import com.karmaos.project.ProjectRepository;
import com.karmaos.flow.FlowRepository;
import com.karmaos.agent.AgentRepository;
import com.karmaos.knowledge.KnowledgePackRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SearchService {

    private final MissionRepository missionRepository;
    private final ProjectRepository projectRepository;
    private final FlowRepository flowRepository;
    private final AgentRepository agentRepository;
    private final KnowledgePackRepository knowledgePackRepository;

    public SearchService(MissionRepository missionRepository, ProjectRepository projectRepository,
                         FlowRepository flowRepository, AgentRepository agentRepository,
                         KnowledgePackRepository knowledgePackRepository) {
        this.missionRepository = missionRepository;
        this.projectRepository = projectRepository;
        this.flowRepository = flowRepository;
        this.agentRepository = agentRepository;
        this.knowledgePackRepository = knowledgePackRepository;
    }

    public Map<String, Object> search(String query, int page, int size) {
        long start = System.currentTimeMillis();
        List<Map<String, Object>> results = new ArrayList<>();

        if (query == null || query.isBlank()) {
            return Map.of("results", List.of(), "total", 0, "took", 0);
        }

        String likePattern = "%" + query.toLowerCase() + "%";

        projectRepository.findByNameContainingIgnoreCase(query).forEach(p -> {
            results.add(Map.of(
                    "id", p.getId(), "type", "project", "name", p.getName(),
                    "description", p.getDescription() != null ? p.getDescription() : ""));
        });

        missionRepository.findByNameContainingIgnoreCase(query).forEach(m -> {
            results.add(Map.of(
                    "id", m.getId(), "type", "mission", "name", m.getName(),
                    "description", m.getDescription() != null ? m.getDescription() : ""));
        });

        flowRepository.findByNameContainingIgnoreCase(query).forEach(f -> {
            results.add(Map.of(
                    "id", f.getId(), "type", "flow", "name", f.getName(),
                    "description", f.getDescription() != null ? f.getDescription() : ""));
        });

        agentRepository.findByNameContainingIgnoreCase(query).forEach(a -> {
            results.add(Map.of(
                    "id", a.getId(), "type", "agent", "name", a.getName(),
                    "description", a.getDescription() != null ? a.getDescription() : ""));
        });

        knowledgePackRepository.findByNameContainingIgnoreCase(query).forEach(k -> {
            results.add(Map.of(
                    "id", k.getId(), "type", "knowledge", "name", k.getName(),
                    "description", k.getDescription() != null ? k.getDescription() : ""));
        });

        long took = System.currentTimeMillis() - start;
        return Map.of("results", results, "total", results.size(), "took", (int) took);
    }
}
