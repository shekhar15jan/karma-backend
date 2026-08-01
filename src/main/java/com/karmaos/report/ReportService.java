package com.karmaos.report;

import com.karmaos.access.OwnershipService;
import com.karmaos.artifact.ArtifactRepository;
import com.karmaos.execution.Execution;
import com.karmaos.execution.ExecutionRepository;
import com.karmaos.mission.Mission;
import com.karmaos.mission.MissionRepository;
import com.karmaos.project.ProjectRepository;
import com.karmaos.provider.ProviderRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final ProjectRepository projectRepository;
    private final MissionRepository missionRepository;
    private final ExecutionRepository executionRepository;
    private final ArtifactRepository artifactRepository;
    private final ProviderRepository providerRepository;
    private final OwnershipService ownershipService;

    public ReportService(ProjectRepository projectRepository, MissionRepository missionRepository,
                         ExecutionRepository executionRepository, ArtifactRepository artifactRepository,
                         ProviderRepository providerRepository, OwnershipService ownershipService) {
        this.projectRepository = projectRepository;
        this.missionRepository = missionRepository;
        this.executionRepository = executionRepository;
        this.artifactRepository = artifactRepository;
        this.providerRepository = providerRepository;
        this.ownershipService = ownershipService;
    }

    public Map<String, Object> dashboard(UUID userId) {
        List<UUID> projectIds = ownershipService.getOwnedProjectIds(userId);
        List<UUID> missionIds = ownershipService.getOwnedMissionIds(userId);
        long totalProjects = projectIds.isEmpty() ? 0 : projectRepository.countByWorkspaceIdInAndStatus(
                ownershipService.getOwnedWorkspaceIds(userId), "ACTIVE");
        long activeMissions = missionIds.isEmpty() ? 0
                : missionRepository.countByProjectIdInAndStatus(projectIds, "RUNNING")
                + missionRepository.countByProjectIdInAndStatus(projectIds, "WAITING");
        long completedToday = missionIds.isEmpty() ? 0 : executionRepository.countByMissionIdInAndStatus(missionIds, "COMPLETED");
        long failed = missionIds.isEmpty() ? 0 : executionRepository.countByMissionIdInAndStatus(missionIds, "FAILED");
        long totalArtifacts = missionIds.isEmpty() ? 0 : artifactRepository.countByMissionIdIn(missionIds);

        Map<String, Object> providerHealth = providerRepository.findAll().stream()
                .filter(p -> p.getDeletedAt() == null)
                .filter(p -> !"PUBLISHING".equalsIgnoreCase(p.getProviderType()))
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

    public Map<String, Object> missions(UUID userId) {
        List<UUID> projectIds = ownershipService.getOwnedProjectIds(userId);
        List<UUID> missionIds = ownershipService.getOwnedMissionIds(userId);
        long totalMissions = missionIds.size();
        long completed = missionIds.isEmpty() ? 0 : missionRepository.findByProjectIdInAndStatus(projectIds, "COMPLETED").size();
        long failed = missionIds.isEmpty() ? 0 : missionRepository.findByProjectIdInAndStatus(projectIds, "FAILED").size();
        long inProgress = missionIds.isEmpty() ? 0
                : missionRepository.findByProjectIdInAndStatus(projectIds, "RUNNING").size()
                + missionRepository.findByProjectIdInAndStatus(projectIds, "WAITING").size();

        List<Execution> ownedExecutions = missionIds.isEmpty()
                ? List.of()
                : executionRepository.findByMissionIdInOrderByCreatedAtDesc(missionIds);
        double avgDuration = ownedExecutions.stream()
                .filter(e -> e.getDurationMs() != null)
                .mapToLong(Execution::getDurationMs)
                .average()
                .orElse(0);
        double avgCost = ownedExecutions.stream()
                .filter(e -> e.getTotalCost() != null)
                .mapToDouble(e -> e.getTotalCost().doubleValue())
                .average()
                .orElse(0);

        Map<String, Long> byType = missionRepository.findByProjectIdInAndStatusNot(projectIds, "DELETED").stream()
                .filter(m -> m.getMissionType() != null)
                .collect(Collectors.groupingBy(Mission::getMissionType, Collectors.counting()));

        return Map.of(
                "totalMissions", totalMissions,
                "completed", completed,
                "failed", failed,
                "inProgress", inProgress,
                "avgDuration", avgDuration,
                "avgCost", avgCost,
                "byType", byType,
                "trend", java.util.Collections.emptyList());
    }
}
