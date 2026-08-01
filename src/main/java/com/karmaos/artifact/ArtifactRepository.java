package com.karmaos.artifact;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ArtifactRepository extends JpaRepository<Artifact, UUID> {

    List<Artifact> findByMissionIdOrderByCreatedAtDesc(UUID missionId);

    List<Artifact> findByMissionIdInOrderByCreatedAtDesc(Collection<UUID> missionIds);

    long countByMissionIdIn(Collection<UUID> missionIds);

    List<Artifact> findByReviewStatus(String reviewStatus);

    List<Artifact> findByReviewStatusAndExecutionStepIdIsNull(String reviewStatus);

    List<Artifact> findByExecutionStepId(UUID executionStepId);

    List<Artifact> findByPublicationStatus(String publicationStatus);
}
