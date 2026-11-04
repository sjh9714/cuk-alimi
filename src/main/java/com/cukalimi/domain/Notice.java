package com.cukalimi.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 공지 한 건. 들어오는 순간부터 검수 대상이다. */
@Entity
@Table(name = "notice")
public class Notice {

    public static final String STATUS_NEEDS_REVIEW = "NEEDS_REVIEW";
    public static final String STATUS_REVIEWED = "REVIEWED";

    /** 날짜를 하나 이상 읽었다. 검수에서 확인만 하면 된다. */
    public static final String REASON_PARSED = "PARSED";
    /** 글자에서 날짜를 못 읽었고 본문에 이미지가 있다. 날짜가 이미지 안에 있을 수 있으니 운영자가 보고 직접 입력한다. */
    public static final String REASON_IMAGE_ONLY = "IMAGE_ONLY";
    /** 글자는 있는데 날짜를 못 읽었다. */
    public static final String REASON_NO_DATE = "NO_DATE";
    /** 본문 페이지를 읽지 못했다(네트워크, 구조 변경). 운영자가 원문을 열어 봐야 한다. */
    public static final String REASON_PARSE_FAILED = "PARSE_FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long articleNo;
    private String category;
    /** 학과 게시판 공지면 학과 이름. 중앙 게시판은 null (ADR 002). */
    private String dept;
    private String title;
    private LocalDate postedOn;
    private String writer;
    private String url;
    private String bodyText;
    private boolean hasImages;
    private String attachments;
    private String status = STATUS_NEEDS_REVIEW;
    private String reviewReason;
    /** 학교가 지웠거나(GONE) 같은 날 같은 제목으로 다시 올려 묵은(DUPLICATE) 공지. 있으면 학생 화면과 알림에서 뺀다. */
    private OffsetDateTime removedAt;
    private String removedReason;
    private OffsetDateTime firstSeenAt = OffsetDateTime.now();
    private OffsetDateTime reviewedAt;
    private String reviewedBy;
    /** 운영자가 고친 대상 범위. 비어 있으면 제목과 본문으로 정한다 (ADR 009). */
    private String audienceScope;
    private String audienceLabel;
    /** AI가 읽은 대상 조건 JSON (ADR 013). 두 번 읽어 같을 때만 있다. 읽은 시각은 어긋나도 남아 다시 부르지 않는다. */
    private String audienceAi;
    private String audienceModel;
    private OffsetDateTime audienceReadAt;
    private long audienceInputTokens;
    private long audienceOutputTokens;

    protected Notice() {
    }

    public Notice(long articleNo, String category, String title, LocalDate postedOn, String writer,
            String url, String bodyText, boolean hasImages, String attachments, String reviewReason) {
        this.articleNo = articleNo;
        this.category = category;
        this.title = title;
        this.postedOn = postedOn;
        this.writer = writer;
        this.url = url;
        this.bodyText = bodyText;
        this.hasImages = hasImages;
        this.attachments = attachments;
        this.reviewReason = reviewReason;
    }

    public Notice(long articleNo, String category, String dept, String title, LocalDate postedOn, String writer,
            String url, String bodyText, boolean hasImages, String attachments, String reviewReason) {
        this(articleNo, category, title, postedOn, writer, url, bodyText, hasImages, attachments, reviewReason);
        this.dept = dept;
    }

    public String getDept() {
        return dept;
    }

    public static final String REMOVED_GONE = "GONE";
    public static final String REMOVED_DUPLICATE = "DUPLICATE";

    public void markRemoved(String reason) {
        if (removedAt == null) {
            this.removedAt = OffsetDateTime.now();
            this.removedReason = reason;
        }
    }

    public boolean isRemoved() {
        return removedAt != null;
    }

    public String getRemovedReason() {
        return removedReason;
    }

    public OffsetDateTime getRemovedAt() {
        return removedAt;
    }

    public void markReviewed(String by) {
        this.status = STATUS_REVIEWED;
        this.reviewedAt = OffsetDateTime.now();
        this.reviewedBy = by;
    }

    /**
     * 다시 처리한 결과로 본문과 사유를 갱신한다. 제목이나 본문이 바뀌었으면(기간 연장, 정정) 다시 검수 큐에 넣는다.
     * 바뀌었으면 true.
     */
    public boolean reparsed(String title, String bodyText, boolean hasImages, String attachments, String reviewReason) {
        boolean changed = !java.util.Objects.equals(this.title, title) || !java.util.Objects.equals(this.bodyText, bodyText);
        if (changed && STATUS_REVIEWED.equals(status)) {
            this.status = STATUS_NEEDS_REVIEW;
            this.reviewedAt = null;
            this.reviewedBy = null;
        }
        if (changed) {
            // 정정 공지는 대상이 바뀌었을 수 있다. 다음 차례에 다시 읽는다.
            this.audienceAi = null;
            this.audienceReadAt = null;
        }
        this.title = title;
        this.bodyText = bodyText;
        this.hasImages = hasImages;
        this.attachments = attachments;
        this.reviewReason = reviewReason;
        return changed;
    }

    public void overrideAudience(String scope, String label) {
        this.audienceScope = scope;
        this.audienceLabel = scope == null ? null : label;
    }

    public Long getId() {
        return id;
    }

    public Long getArticleNo() {
        return articleNo;
    }

    public String getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getPostedOn() {
        return postedOn;
    }

    public String getUrl() {
        return url;
    }

    public boolean isHasImages() {
        return hasImages;
    }

    public String getAttachments() {
        return attachments;
    }

    public String getStatus() {
        return status;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public OffsetDateTime getFirstSeenAt() {
        return firstSeenAt;
    }

    public String getBodyText() {
        return bodyText;
    }

    public String getAudienceScope() {
        return audienceScope;
    }

    public String getAudienceLabel() {
        return audienceLabel;
    }

    /** factsJson이 null이면 두 번 읽은 결과가 어긋났다는 뜻이다. 토큰은 쌓아서 센다. */
    public void recordAudienceRead(String factsJson, String model, long inputTokens, long outputTokens, OffsetDateTime at) {
        this.audienceAi = factsJson;
        this.audienceModel = model;
        this.audienceReadAt = at;
        this.audienceInputTokens += inputTokens;
        this.audienceOutputTokens += outputTokens;
    }

    public String getAudienceAi() {
        return audienceAi;
    }

    public String getAudienceModel() {
        return audienceModel;
    }

    public OffsetDateTime getAudienceReadAt() {
        return audienceReadAt;
    }
}
