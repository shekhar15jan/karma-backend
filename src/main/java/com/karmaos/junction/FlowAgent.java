package com.karmaos.junction;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.Column;

@Entity
@Table(name = "flow_agents")
@Getter
@Setter
@NoArgsConstructor
public class FlowAgent {

    @EmbeddedId
    private FlowAgentId id;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;
}
