package com.karmaos.flow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlowRepository extends JpaRepository<Flow, UUID> {

    List<Flow> findByStatus(String status);

    List<Flow> findByCategoryAndStatus(String category, String status);

    Optional<Flow> findByIdAndStatus(UUID id, String status);

    List<Flow> findByNameContainingIgnoreCase(String name);
}
