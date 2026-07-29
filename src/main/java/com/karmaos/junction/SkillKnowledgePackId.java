package com.karmaos.junction;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillKnowledgePackId implements Serializable {
    private UUID skillId;
    private UUID knowledgePackId;
}
