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
public class AgentKnowledgePackId implements Serializable {
    private UUID agentId;
    private UUID knowledgePackId;
}
