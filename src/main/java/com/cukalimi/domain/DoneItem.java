package com.cukalimi.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "done_item")
@IdClass(DoneItem.Key.class)
public class DoneItem {

    @Id
    private Long userId;
    @Id
    private Long dateItemId;
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected DoneItem() {
    }

    public DoneItem(Long userId, Long dateItemId) {
        this.userId = userId;
        this.dateItemId = dateItemId;
    }

    public Long getDateItemId() {
        return dateItemId;
    }

    public static class Key implements Serializable {
        private Long userId;
        private Long dateItemId;

        public Key() {
        }

        public Key(Long userId, Long dateItemId) {
            this.userId = userId;
            this.dateItemId = dateItemId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(dateItemId, k.dateItemId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, dateItemId);
        }
    }
}
