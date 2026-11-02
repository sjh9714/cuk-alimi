package com.cukalimi.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 사용자. 프로필 네 칸은 전부 선택이다 (ADR 008). */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String email;
    private Integer entryYear;
    private String major;
    private String secondMajor;
    private String homeRegion;
    /** 아침 알림 메일을 받는가. 메일 안의 링크 하나로 끈다 (ADR 004). */
    private boolean digestEnabled = true;
    private String unsubscribeToken = java.util.UUID.randomUUID().toString().replace("-", "");
    private OffsetDateTime createdAt = OffsetDateTime.now();
    private OffsetDateTime lastLoginAt;

    protected AppUser() {
    }

    public AppUser(String email) {
        this.email = email;
    }

    public void updateProfile(Integer entryYear, String major, String secondMajor, String homeRegion) {
        this.entryYear = entryYear;
        this.major = blankToNull(major);
        this.secondMajor = blankToNull(secondMajor);
        this.homeRegion = blankToNull(homeRegion);
    }

    public void setDigestEnabled(boolean enabled) {
        this.digestEnabled = enabled;
    }

    public boolean isDigestEnabled() {
        return digestEnabled;
    }

    public String getUnsubscribeToken() {
        return unsubscribeToken;
    }

    public void touchLogin() {
        lastLoginAt = OffsetDateTime.now();
    }

    /**
     * 입학년도로 학년을 센다. 학년도는 3월에 시작하므로 1·2월은 전년도로 본다.
     * 5학년 이상은 4로 본다. 휴학·복학은 모르니 어림값이고, 화면에서 바꿀 수 있다.
     */
    public Integer grade(LocalDate today) {
        if (entryYear == null) {
            return null;
        }
        int academicYear = today.getMonthValue() >= 3 ? today.getYear() : today.getYear() - 1;
        int grade = academicYear - entryYear + 1;
        return Math.max(1, Math.min(4, grade));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Integer getEntryYear() {
        return entryYear;
    }

    public String getMajor() {
        return major;
    }

    public String getSecondMajor() {
        return secondMajor;
    }

    public String getHomeRegion() {
        return homeRegion;
    }
}
