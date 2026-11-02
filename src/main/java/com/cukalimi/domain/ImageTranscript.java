package com.cukalimi.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** 공지 그림 한 장을 AI가 받아 적은 글. 날짜 판단은 여기서 하지 않는다. 이 글을 공지 날짜 규칙에 그대로 넣는다 (ADR 010). */
@Entity
@Table(name = "image_transcript")
public class ImageTranscript {

    @Id
    private String imageUrl;
    private Long articleNo;
    private String text;
    private String model;
    private long inputTokens;
    private long outputTokens;
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected ImageTranscript() {
    }

    public ImageTranscript(String imageUrl, long articleNo, String text, String model, long inputTokens,
            long outputTokens) {
        this.imageUrl = imageUrl;
        this.articleNo = articleNo;
        this.text = text;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Long getArticleNo() {
        return articleNo;
    }

    public String getText() {
        return text;
    }

    public String getModel() {
        return model;
    }

    public long getInputTokens() {
        return inputTokens;
    }

    public long getOutputTokens() {
        return outputTokens;
    }
}
