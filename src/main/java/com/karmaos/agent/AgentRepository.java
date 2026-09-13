package com.karmaos.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentRepository extends JpaRepository<Agent, UUID> {

    List<Agent> findByStatus(String status);

    List<Agent> findByStatusNot(String status);

    List<Agent> findByCategoryAndStatus(String category, String status);

    Optional<Agent> findByIdAndStatus(UUID id, String status);

    List<Agent> findByNameContainingIgnoreCase(String name);
}
