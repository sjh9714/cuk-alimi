package com.cukalimi.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "notice_raw")
public class NoticeRaw {

    @Id
    private Long articleNo;
    private String html;
    private OffsetDateTime fetchedAt = OffsetDateTime.now();

    protected NoticeRaw() {
    }

    public NoticeRaw(long articleNo, String html) {
        this.articleNo = articleNo;
        this.html = html;
    }

    public Long getArticleNo() {
        return articleNo;
    }

    public String getHtml() {
        return html;
    }
}
