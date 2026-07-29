package com.karmaos.artifactversion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArtifactVersionRepository extends JpaRepository<ArtifactVersion, UUID> {

    List<ArtifactVersion> findByArtifactIdOrderByVersionDesc(Long artifactId);

    Optional<ArtifactVersion> findByArtifactIdAndVersion(Long artifactId, int version);

    Optional<ArtifactVersion> findTopByArtifactIdOrderByVersionDesc(Long artifactId);
}
