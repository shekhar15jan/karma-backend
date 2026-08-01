package com.karmaos.execution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExecutionRepository extends JpaRepository<Execution, UUID> {

    List<Execution> findByMissionIdOrderByCreatedAtDesc(UUID missionId);

    List<Execution> findByMissionIdInOrderByCreatedAtDesc(Collection<UUID> missionIds);

    Optional<Execution> findFirstByMissionIdOrderByCreatedAtDesc(UUID missionId);

    Optional<Execution> findByIdAndStatus(UUID id, String status);

    List<Execution> findByStatus(String status);

    long countByMissionIdAndStatus(UUID missionId, String status);

    long countByMissionIdInAndStatus(Collection<UUID> missionIds, String status);

    long countByStatus(String status);
}
