package com.karmaos.execution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExecutionStepRepository extends JpaRepository<ExecutionStep, UUID> {

    List<ExecutionStep> findByExecutionIdOrderByStepOrder(UUID executionId);

    List<ExecutionStep> findByExecutionIdAndStatus(UUID executionId, String status);

    long countByExecutionIdAndStatus(UUID executionId, String status);

    List<ExecutionStep> findByAgentIdOrderByCreatedAtDesc(UUID agentId);

    List<ExecutionStep> findByReviewStatus(String reviewStatus);

    List<ExecutionStep> findByReviewStatusInAndExecutionIdIn(Collection<String> reviewStatuses,
                                                             Collection<UUID> executionIds);
}
