package com.cukalimi.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 학생 혜택 한 줄. benefits.yml이 원본이고 이 표는 그 사본이다. */
@Entity
@Table(name = "benefit")
public class Benefit {

    /** 확인일이 이 날수를 넘으면 "재확인 필요"로 표시하고 알림에서 뺀다 (ADR 003). */
    public static final int STALE_AFTER_DAYS = 90;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String slug;
    private String name;
    private String category;
    private String summary;
    private String condition;
    private String howTo;
    private String price;
    private String sourceName;
    private String sourceUrl;
    private LocalDate verifiedOn;
    private String renewalRule;
    private LocalDate endsOn;
    private boolean active = true;
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected Benefit() {
    }

    public Benefit(String slug) {
        this.slug = slug;
    }

    /** 파일 내용으로 덮어쓴다. 바뀐 게 있으면 true. */
    public boolean apply(BenefitSeed seed) {
        boolean changed = !seed.name().equals(name)
                || !seed.category().equals(category)
                || !seed.summary().equals(summary)
                || !seed.condition().equals(condition)
                || !seed.howTo().equals(howTo)
                || !java.util.Objects.equals(seed.price(), price)
                || !seed.sourceName().equals(sourceName)
                || !seed.sourceUrl().equals(sourceUrl)
                || !seed.verifiedOn().equals(verifiedOn)
                || !seed.renewalRule().equals(renewalRule)
                || !java.util.Objects.equals(seed.endsOn(), endsOn)
                || !active;
        if (changed) {
            name = seed.name();
            category = seed.category();
            summary = seed.summary();
            condition = seed.condition();
            howTo = seed.howTo();
            price = seed.price();
            sourceName = seed.sourceName();
            sourceUrl = seed.sourceUrl();
            verifiedOn = seed.verifiedOn();
            renewalRule = seed.renewalRule();
            endsOn = seed.endsOn();
            active = true;
            updatedAt = OffsetDateTime.now();
        }
        return changed;
    }

    /** 파일에서 빠진 항목. 지우지 않고 숨긴다. 바뀐 게 있으면 true. */
    public boolean deactivate() {
        if (!active) {
            return false;
        }
        active = false;
        updatedAt = OffsetDateTime.now();
        return true;
    }

    public boolean isStale(LocalDate today) {
        return verifiedOn.plusDays(STALE_AFTER_DAYS).isBefore(today);
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getSummary() {
        return summary;
    }

    public String getCondition() {
        return condition;
    }

    public String getHowTo() {
        return howTo;
    }

    public String getPrice() {
        return price;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public LocalDate getVerifiedOn() {
        return verifiedOn;
    }

    public String getRenewalRule() {
        return renewalRule;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public boolean isActive() {
        return active;
    }
}
