package com.karmaos.memory;

import com.karmaos.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "karma_user_memories",
        uniqueConstraints = @UniqueConstraint(name = "uk_karma_user_memory", columnNames = {"user_id", "memory_key"}),
        indexes = @Index(name = "idx_karma_user_memories_user", columnList = "user_id, confidence DESC"))
@Getter
@Setter
@NoArgsConstructor
public class KarmaUserMemory extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private java.util.UUID userId;

    @Column(name = "memory_key", length = 100, nullable = false)
    private String memoryKey;

    @Column(name = "memory_value", length = 500, nullable = false)
    private String memoryValue;

    @Column(length = 20)
    private String source = "AUTO";

    @Column
    private Integer confidence = 50;

    public KarmaUserMemory(java.util.UUID userId, String memoryKey, String memoryValue) {
        this.userId = userId;
        this.memoryKey = memoryKey;
        this.memoryValue = memoryValue;
    }
}
