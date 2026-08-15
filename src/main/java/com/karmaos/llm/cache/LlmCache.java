package com.karmaos.llm.cache;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "llm_cache")
public class LlmCache {

    @Id
    @Column(name = "hash_key")
    private String hashKey;

    @Column(name = "model")
    private String model;

    @Column(name = "response_content", columnDefinition = "TEXT")
    private String responseContent;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public String getHashKey() { return hashKey; }
    public void setHashKey(String hashKey) { this.hashKey = hashKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getResponseContent() { return responseContent; }
    public void setResponseContent(String responseContent) { this.responseContent = responseContent; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
