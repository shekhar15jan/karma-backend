package com.karmaos.provider;

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
@Table(name = "providers")
@Getter
@Setter
@NoArgsConstructor
public class Provider extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "provider_type", nullable = false, length = 50)
    private String providerType;

    @Column(name = "api_endpoint", length = 500)
    private String apiEndpoint;

    @Column(name = "encrypted_api_key", columnDefinition = "BYTEA")
    private byte[] encryptedApiKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB DEFAULT '[]'::jsonb")
    private String models = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB DEFAULT '[]'::jsonb")
    private String capabilities = "[]";

    @Column(nullable = false, length = 50)
    private String status = "DISCONNECTED";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB DEFAULT '{}'::jsonb")
    private String configuration = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB DEFAULT '{}'::jsonb")
    private String health = "{}";
}
