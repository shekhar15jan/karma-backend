package com.karmaos.mcp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface McpServerRepository extends JpaRepository<McpServer, UUID> {
    List<McpServer> findAllByStatus(String status);
    Optional<McpServer> findByIdAndStatus(UUID id, String status);
}
