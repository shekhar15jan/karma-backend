package com.karmaos.junction;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "agent_skills")
@Getter
@Setter
@NoArgsConstructor
public class AgentSkill {

    @EmbeddedId
    private AgentSkillId id;
}
