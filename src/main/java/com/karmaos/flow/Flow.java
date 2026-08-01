package com.karmaos.flow;

import com.karmaos.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "flows")
@Getter
@Setter
@NoArgsConstructor
public class Flow extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50)
    private String category;

    @Column(name = "execution_order", nullable = false)
    private int executionOrder = 0;

    @Column(nullable = false)
    private boolean enabled = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB DEFAULT '{}'::jsonb")
    private String parameters = "{}";
}
