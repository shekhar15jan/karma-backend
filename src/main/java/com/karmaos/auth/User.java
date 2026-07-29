package com.karmaos.auth;

import com.karmaos.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(length = 50)
    private String role = "USER";

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "preferences")
    @JdbcTypeCode(SqlTypes.JSON)
    private String preferences = "{}";

    public User(String email, String password, String displayName) {
        this.email = email;
        this.password = password;
        this.displayName = displayName;
        this.role = "USER";
    }
}
