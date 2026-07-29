package com.karmaos.artifact;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ArtifactRepository extends JpaRepository<Artifact, Long> {

    List<Artifact> findByMissionIdOrderByCreatedAtDesc(UUID missionId);

    List<Artifact> findByReviewStatus(String reviewStatus);

    List<Artifact> findByPublicationStatus(String publicationStatus);
}
