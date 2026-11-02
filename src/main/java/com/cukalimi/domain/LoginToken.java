package com.cukalimi.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "login_token")
public class LoginToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String email;
    private String tokenHash;
    private OffsetDateTime expiresAt;
    private OffsetDateTime usedAt;
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected LoginToken() {
    }

    public LoginToken(String email, String tokenHash, OffsetDateTime expiresAt) {
        this.email = email;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(OffsetDateTime now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed() {
        usedAt = OffsetDateTime.now();
    }

    public String getEmail() {
        return email;
    }
}
