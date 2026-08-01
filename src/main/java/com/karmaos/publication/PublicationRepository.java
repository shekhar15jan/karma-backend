package com.karmaos.publication;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface PublicationRepository extends JpaRepository<Publication, UUID> {

    List<Publication> findByArtifactIdOrderByCreatedAtDesc(UUID artifactId);

    List<Publication> findByArtifactIdInOrderByCreatedAtDesc(Collection<UUID> artifactIds);

    List<Publication> findByStatus(String status);
}
