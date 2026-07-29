package com.karmaos.report;

import com.karmaos.artifact.ArtifactRepository;
import com.karmaos.execution.ExecutionRepository;
import com.karmaos.mission.MissionRepository;
import com.karmaos.project.ProjectRepository;
import com.karmaos.provider.ProviderRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final ProjectRepository projectRepository;
    private final MissionRepository missionRepository;
    private final ExecutionRepository executionRepository;
    private final ArtifactRepository artifactRepository;
    private final ProviderRepository providerRepository;

    public ReportService(ProjectRepository projectRepository, MissionRepository missionRepository,
                         ExecutionRepository executionRepository, ArtifactRepository artifactRepository,
                         ProviderRepository providerRepository) {
        this.projectRepository = projectRepository;
        this.missionRepository = missionRepository;
        this.executionRepository = executionRepository;
        this.artifactRepository = artifactRepository;
        this.providerRepository = providerRepository;
    }

    public Map<String, Object> dashboard() {
        long totalProjects = projectRepository.count();
        long activeMissions = missionRepository.countByStatus("RUNNING");
        long completedToday = executionRepository.countByStatus("COMPLETED");
        long failed = executionRepository.countByStatus("FAILED");
        long totalArtifacts = artifactRepository.count();

        Map<String, Object> providerHealth = providerRepository.findAll().stream()
                .collect(Collectors.toMap(
                        p -> p.getId().toString(),
                        p -> Map.of("name", p.getName(), "status", p.getStatus())));

        return Map.of(
                "totalProjects", totalProjects,
                "activeWorkflows", activeMissions,
                "completedToday", completedToday,
                "failed", failed,
                "totalArtifacts", totalArtifacts,
                "providerHealth", providerHealth);
    }

    public Map<String, Object> missions() {
        long totalMissions = missionRepository.count();
        long completed = missionRepository.countByStatus("COMPLETED");
        long failed = missionRepository.countByStatus("FAILED");
        long inProgress = missionRepository.countByStatus("RUNNING");

        return Map.of(
                "totalMissions", totalMissions,
                "completed", completed,
                "failed", failed,
                "inProgress", inProgress,
                "avgDuration", 0,
                "avgCost", 0,
                "byType", Map.of(),
                "trend", java.util.Collections.emptyList());
    }
}
