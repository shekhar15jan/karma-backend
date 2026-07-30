package com.karmaos.junction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FlowAgentRepository extends JpaRepository<FlowAgent, FlowAgentId> {

    List<FlowAgent> findByIdFlowIdOrderByStepOrderAsc(UUID flowId);

    List<FlowAgent> findByIdFlowId(UUID flowId);

    List<FlowAgent> findByIdAgentId(UUID agentId);
}
