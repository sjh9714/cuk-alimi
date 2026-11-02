package com.cukalimi.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user_session")
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String tokenHash;
    private OffsetDateTime expiresAt;
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected UserSession() {
    }

    public UserSession(Long userId, String tokenHash, OffsetDateTime expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isAlive(OffsetDateTime now) {
        return expiresAt.isAfter(now);
    }

    public Long getUserId() {
        return userId;
    }
}
