package com.cukalimi.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** 문장 찾기 AI가 공지를 한 번 읽은 기록 (ADR 016). answer는 모델의 원본 답(JSON)이고 실패하면 비어 있다. */
@Entity
@Table(name = "locator_reading")
public class LocatorRead {

    public static final String STATUS_READ = "READ";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long noticeId;
    private String model;
    private String promptHash;
    private String status;
    private String answer;
    private long inputTokens;
    private long outputTokens;
    private OffsetDateTime createdAt;

    protected LocatorRead() {
    }

    public LocatorRead(long noticeId, String model, String promptHash, String status, String answer, long inputTokens,
            long outputTokens, OffsetDateTime createdAt) {
        this.noticeId = noticeId;
        this.model = model;
        this.promptHash = promptHash;
        this.status = status;
        this.answer = answer;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.createdAt = createdAt;
    }

    public Long getNoticeId() {
        return noticeId;
    }

    public String getModel() {
        return model;
    }

    public String getStatus() {
        return status;
    }

    public String getAnswer() {
        return answer;
    }
}
