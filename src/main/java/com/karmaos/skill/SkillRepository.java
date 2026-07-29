package com.karmaos.skill;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SkillRepository extends JpaRepository<Skill, UUID> {

    List<Skill> findByStatusNot(String status);
}
