package com.cukalimi.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "fetch_log")
public class FetchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String source;
    private String url;
    private Integer httpStatus;
    private String error;
    private String html;
    private OffsetDateTime fetchedAt = OffsetDateTime.now();

    protected FetchLog() {
    }

    public static FetchLog ok(String source, String url, int status) {
        FetchLog log = new FetchLog();
        log.source = source;
        log.url = url;
        log.httpStatus = status;
        return log;
    }

    public static FetchLog failed(String source, String url, String error) {
        FetchLog log = new FetchLog();
        log.source = source;
        log.url = url;
        log.error = error;
        return log;
    }

    public Long getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getError() {
        return error;
    }
}
