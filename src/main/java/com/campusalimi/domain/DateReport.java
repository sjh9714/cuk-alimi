package com.campusalimi.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** 학생이 남긴 "날짜가 달라요". 공개 정확도의 실사용 지표이기도 하다. */
@Entity
@Table(name = "date_report")
public class DateReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long dateItemId;
    private Long userId;
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected DateReport() {
    }

    public DateReport(Long dateItemId, Long userId) {
        this.dateItemId = dateItemId;
        this.userId = userId;
    }
}
