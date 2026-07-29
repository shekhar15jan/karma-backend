package com.karmaos.mission;

import com.karmaos.flow.Flow;
import com.karmaos.project.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MissionRepository extends JpaRepository<Mission, UUID> {

    List<Mission> findByProjectIdAndStatus(UUID projectId, String status);

    List<Mission> findByProjectId(UUID projectId);

    Optional<Mission> findByIdAndStatus(UUID id, String status);

    long countByProjectIdAndStatus(UUID projectId, String status);

    long countByProjectId(UUID projectId);

    long countByStatus(String status);

    List<Mission> findByNameContainingIgnoreCase(String name);
}
