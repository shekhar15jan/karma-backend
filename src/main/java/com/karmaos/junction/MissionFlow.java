package com.karmaos.junction;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "mission_flows")
@Getter
@Setter
@NoArgsConstructor
public class MissionFlow {

    @EmbeddedId
    private MissionFlowId id;

    @Column(name = "exec_order", nullable = false)
    private int execOrder = 0;

    @Column(nullable = false, length = 50)
    private String status = "PENDING";
}
