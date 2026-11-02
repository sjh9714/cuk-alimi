package com.cukalimi.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** 사용자가 남긴 "이건 왜 없나요?" 한 줄. 운영자가 검수 화면에서 읽는다. */
@Entity
@Table(name = "feedback")
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String message;
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected Feedback() {
    }

    public Feedback(Long userId, String message) {
        this.userId = userId;
        this.message = message;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getMessage() {
        return message;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
