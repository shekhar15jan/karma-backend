package com.karmaos.junction;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "skill_knowledge_packs")
@Getter
@Setter
@NoArgsConstructor
public class SkillKnowledgePack {

    @EmbeddedId
    private SkillKnowledgePackId id;
}
