package com.karmaos.publication;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PublicationRepository extends JpaRepository<Publication, Long> {

    List<Publication> findByArtifactIdOrderByCreatedAtDesc(Long artifactId);

    List<Publication> findByStatus(String status);
}
