package com.cukalimi.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 운영자의 검수 동작 한 번. 항목 단위 동작은 고치기 전후 값을 JSON으로 남긴다.
 * 날짜 항목 없이 공지를 승인한 기록(dateItemId 없음)은 "이 공지에는 마감이 없다"는 판단이다.
 */
@Entity
@Table(name = "review_event")
public class ReviewEvent {

    public static final String APPROVE = "APPROVE";
    public static final String EDIT = "EDIT";
    public static final String DELETE = "DELETE";
    public static final String ADD = "ADD";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long noticeId;
    private Long dateItemId;
    private String action;
    private String beforeJson;
    private String afterJson;
    private String reviewedBy;
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected ReviewEvent() {
    }

    public ReviewEvent(Long noticeId, Long dateItemId, String action, String beforeJson, String afterJson,
            String reviewedBy) {
        this.noticeId = noticeId;
        this.dateItemId = dateItemId;
        this.action = action;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.reviewedBy = reviewedBy;
    }

    public Long getNoticeId() {
        return noticeId;
    }

    public Long getDateItemId() {
        return dateItemId;
    }

    public String getAction() {
        return action;
    }

    public String getBeforeJson() {
        return beforeJson;
    }

    public String getAfterJson() {
        return afterJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
