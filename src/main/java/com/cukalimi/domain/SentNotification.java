package com.cukalimi.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/** 보낸 알림 한 건. 키는 "notice:12", "saved:34", "benefit:5"처럼 사건을 가리킨다 (ADR 004). */
@Entity
@Table(name = "sent_notification")
@IdClass(SentNotification.Key.class)
public class SentNotification {

    @Id
    private Long userId;
    @Id
    private String eventKey;
    private OffsetDateTime sentAt = OffsetDateTime.now();

    protected SentNotification() {
    }

    public SentNotification(Long userId, String eventKey) {
        this.userId = userId;
        this.eventKey = eventKey;
    }

    public String getEventKey() {
        return eventKey;
    }

    public static class Key implements Serializable {
        private Long userId;
        private String eventKey;

        public Key() {
        }

        public Key(Long userId, String eventKey) {
            this.userId = userId;
            this.eventKey = eventKey;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(eventKey, k.eventKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, eventKey);
        }
    }
}
