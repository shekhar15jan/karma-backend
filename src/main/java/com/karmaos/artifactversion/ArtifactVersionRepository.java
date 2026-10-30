package com.karmaos.artifactversion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArtifactVersionRepository extends JpaRepository<ArtifactVersion, UUID> {

    List<ArtifactVersion> findByArtifactIdOrderByVersionDesc(UUID artifactId);

    Optional<ArtifactVersion> findByArtifactIdAndVersion(UUID artifactId, int version);

    Optional<ArtifactVersion> findTopByArtifactIdOrderByVersionDesc(UUID artifactId);
}
