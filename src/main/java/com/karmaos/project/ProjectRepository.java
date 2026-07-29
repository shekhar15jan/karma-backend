package com.karmaos.project;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByWorkspaceIdAndStatus(UUID workspaceId, String status);

    Optional<Project> findByIdAndStatus(UUID id, String status);

    long countByWorkspaceIdAndStatus(UUID workspaceId, String status);

    List<Project> findByNameContainingIgnoreCase(String name);
}
