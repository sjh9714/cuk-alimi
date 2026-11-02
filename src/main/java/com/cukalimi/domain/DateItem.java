package com.cukalimi.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/** 화면에 나가는 날짜 한 줄. 학사일정, 공지에서 뽑은 것, 운영자가 손으로 넣은 것이 모두 같은 모양이다. */
@Entity
@Table(name = "date_item")
public class DateItem {

    public static final String SOURCE_SCHEDULE = "SCHEDULE";
    public static final String SOURCE_NOTICE = "NOTICE";
    public static final String SOURCE_MANUAL = "MANUAL";

    public static final String STATUS_AUTO = "AUTO";
    public static final String STATUS_REVIEWED = "REVIEWED";
    /** 공지 그림을 AI가 받아 적은 글에서 읽은 날짜. 운영자가 승인하기 전까지 학생에게 보이지 않는다 (ADR 010). */
    public static final String STATUS_CANDIDATE = "CANDIDATE";
    /** 그림을 두 번 받아 적어 날짜가 일치해 자동으로 공개한 항목 (ADR 010). */
    public static final String STATUS_AUTO_IMAGE = "AUTO_IMAGE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String sourceKind;
    private Long noticeId;
    private String naturalKey;
    private String title;
    private LocalDate startsOn;
    private LocalDate endsOn;
    private LocalTime startsAt;
    private LocalTime endsAt;
    private String campus;
    private String targetGrade;
    private String targetDept;
    private String targetNote;
    private String status = STATUS_AUTO;
    private OffsetDateTime withdrawnAt;
    private OffsetDateTime createdAt = OffsetDateTime.now();
    private OffsetDateTime updatedAt = OffsetDateTime.now();
    private OffsetDateTime reviewedAt;
    private String reviewedBy;

    protected DateItem() {
    }

    public static DateItem fromSchedule(String naturalKey, String title, LocalDate startsOn, LocalDate endsOn,
            String campus) {
        DateItem item = new DateItem();
        item.sourceKind = SOURCE_SCHEDULE;
        item.naturalKey = naturalKey;
        item.title = title;
        item.startsOn = startsOn;
        item.endsOn = endsOn;
        item.campus = campus;
        item.targetGrade = "ALL";
        item.targetDept = "ALL";
        // 학교가 표로 준 날짜다. 파싱이 없으니 사람이 다시 볼 것이 없다.
        item.status = STATUS_REVIEWED;
        item.reviewedBy = "schedule";
        item.reviewedAt = OffsetDateTime.now();
        return item;
    }

    public static DateItem fromNotice(Long noticeId, String naturalKey, String title, LocalDate startsOn,
            LocalDate endsOn, LocalTime startsAt, LocalTime endsAt, String targetGrade) {
        DateItem item = new DateItem();
        item.sourceKind = SOURCE_NOTICE;
        item.noticeId = noticeId;
        item.naturalKey = naturalKey;
        item.title = title;
        item.startsOn = startsOn;
        item.endsOn = endsOn;
        item.startsAt = startsAt;
        item.endsAt = endsAt;
        item.targetGrade = targetGrade;
        return item;
    }

    /** 그림을 받아 적은 글에서 읽은 항목. 승인 전 후보다. */
    public static DateItem fromImage(Long noticeId, String naturalKey, String title, LocalDate startsOn,
            LocalDate endsOn, LocalTime startsAt, LocalTime endsAt, String targetGrade) {
        DateItem item = fromNotice(noticeId, naturalKey, title, startsOn, endsOn, startsAt, endsAt, targetGrade);
        item.status = STATUS_CANDIDATE;
        return item;
    }

    /** 두 번 읽기가 일치해 학생에게 공개한다. 사람이 이미 판단한 항목은 건드리지 않는다. */
    public void publishFromImage() {
        if (STATUS_CANDIDATE.equals(status)) {
            status = STATUS_AUTO_IMAGE;
            updatedAt = OffsetDateTime.now();
        }
    }

    /** 신고가 온 자동 공개 날짜를 내려 운영자에게 넘긴다. 내렸으면 true. */
    public boolean withdrawFromImage() {
        if (!STATUS_AUTO_IMAGE.equals(status)) {
            return false;
        }
        status = STATUS_CANDIDATE;
        withdrawnAt = OffsetDateTime.now();
        updatedAt = withdrawnAt;
        return true;
    }

    public boolean isWithdrawn() {
        return withdrawnAt != null;
    }

    /** 운영자가 손으로 넣은 항목. 이미지에만 날짜가 있는 공지가 여기로 온다. */
    public static DateItem manual(Long noticeId, String title, LocalDate startsOn, LocalDate endsOn, LocalTime startsAt,
            LocalTime endsAt, String targetGrade, String targetDept, String targetNote, String by) {
        DateItem item = new DateItem();
        item.sourceKind = SOURCE_MANUAL;
        item.noticeId = noticeId;
        item.naturalKey = "manual:" + java.util.UUID.randomUUID();
        item.review(title, startsOn, endsOn, startsAt, endsAt, targetGrade, targetDept, targetNote, by);
        return item;
    }

    /** 검수. 사람이 확인하거나 고친 뒤에는 수집이 덮어쓰지 않는다. */
    public void review(String title, LocalDate startsOn, LocalDate endsOn, LocalTime startsAt, LocalTime endsAt,
            String targetGrade, String targetDept, String targetNote, String by) {
        this.title = title;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.targetGrade = targetGrade;
        this.targetDept = targetDept;
        this.targetNote = targetNote;
        markReviewed(by);
    }

    public void markReviewed(String by) {
        this.status = STATUS_REVIEWED;
        this.reviewedAt = OffsetDateTime.now();
        this.reviewedBy = by;
        this.updatedAt = reviewedAt;
    }

    /** 수집 결과로 덮어쓴다. 사람이 고친 항목은 건드리지 않고, 학사일정 항목은 표가 바뀌면 따라간다. */
    public boolean refreshFrom(DateItem fresh) {
        if (!STATUS_AUTO.equals(status) && !"schedule".equals(reviewedBy)) {
            return false;
        }
        boolean changed = !fresh.title.equals(title)
                || !java.util.Objects.equals(fresh.startsOn, startsOn)
                || !fresh.endsOn.equals(endsOn)
                || !java.util.Objects.equals(fresh.startsAt, startsAt)
                || !java.util.Objects.equals(fresh.endsAt, endsAt);
        if (changed) {
            title = fresh.title;
            startsOn = fresh.startsOn;
            endsOn = fresh.endsOn;
            startsAt = fresh.startsAt;
            endsAt = fresh.endsAt;
            updatedAt = OffsetDateTime.now();
        }
        return changed;
    }

    public Long getId() {
        return id;
    }

    public String getSourceKind() {
        return sourceKind;
    }

    public Long getNoticeId() {
        return noticeId;
    }

    public String getNaturalKey() {
        return naturalKey;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public LocalTime getStartsAt() {
        return startsAt;
    }

    public LocalTime getEndsAt() {
        return endsAt;
    }

    public String getCampus() {
        return campus;
    }

    public String getTargetGrade() {
        return targetGrade;
    }

    public String getTargetDept() {
        return targetDept;
    }

    public String getTargetNote() {
        return targetNote;
    }

    public String getStatus() {
        return status;
    }
}
