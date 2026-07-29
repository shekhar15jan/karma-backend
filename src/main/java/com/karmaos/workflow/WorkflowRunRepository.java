package com.karmaos.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, UUID> {

    List<WorkflowRun> findByProjectIdOrderByStartedAtDesc(UUID projectId);

    List<WorkflowRun> findByStatus(String status);
}
