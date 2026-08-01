package com.karmaos.memory;

import com.karmaos.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "karma_interaction_events", indexes = {
        @Index(name = "idx_karma_interaction_events_user_created", columnList = "user_id, created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
public class KarmaInteractionEvent extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private java.util.UUID userId;

    @Column(name = "user_message", columnDefinition = "TEXT")
    private String userMessage;

    @Column(name = "karma_reply", columnDefinition = "TEXT")
    private String karmaReply;

    @Column(name = "action_type", length = 50)
    private String actionType;

    @Column(name = "action_params", columnDefinition = "TEXT")
    private String actionParams;

    @Column(name = "action_status", length = 20)
    private String actionStatus = "PENDING";

    @Column(name = "outcome_note", columnDefinition = "TEXT")
    private String outcomeNote;

    @Column(name = "page", length = 100)
    private String page;

    public KarmaInteractionEvent(java.util.UUID userId, String userMessage) {
        this.userId = userId;
        this.userMessage = userMessage;
    }
}
