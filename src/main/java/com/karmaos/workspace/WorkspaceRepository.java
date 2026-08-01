package com.karmaos.workspace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    List<Workspace> findByOwnerIdAndStatus(UUID ownerId, String status);

    Optional<Workspace> findByIdAndOwnerIdAndStatus(UUID id, UUID ownerId, String status);

    boolean existsByOwnerIdAndNameIgnoreCaseAndStatus(UUID ownerId, String name, String status);

    boolean existsByOwnerIdAndNameIgnoreCaseAndStatusAndIdNot(UUID ownerId, String name, String status, UUID id);

    @Query("SELECT COUNT(w) FROM Workspace w WHERE w.ownerId = ?1 AND w.status = 'ACTIVE'")
    long countActiveByOwnerId(UUID ownerId);
}
