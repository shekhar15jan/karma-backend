package com.karmaos.junction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MissionFlowRepository extends JpaRepository<MissionFlow, MissionFlowId> {

    List<MissionFlow> findByIdMissionId(UUID missionId);

    List<MissionFlow> findByIdFlowId(UUID flowId);

    void deleteByIdMissionId(UUID missionId);
}
