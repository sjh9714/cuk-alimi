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
    /** 입학년도로 센 학년과 실제 학년의 차이. 휴학이나 군 복무로 늦어지면 음수다. */
    private int gradeAdjust;
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

    /** grade는 학생이 고른 지금 학년이다. null이면 입학년도로 센다. */
    public void updateProfile(Integer entryYear, Integer grade, String major, String secondMajor, String homeRegion,
            LocalDate today) {
        this.entryYear = entryYear;
        this.gradeAdjust = entryYear == null || grade == null ? 0 : grade - yearsSince(entryYear, today);
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

    /** 지금 학년. 학생이 고른 차이를 입학년도로 센 값에 더해서 다음 해에도 한 학년씩 오른다. */
    public Integer grade(LocalDate today) {
        return entryYear == null ? null : clamp(yearsSince(entryYear, today) + gradeAdjust);
    }

    /** 휴학을 모르고 입학년도로만 센 학년. */
    public Integer autoGrade(LocalDate today) {
        return entryYear == null ? null : clamp(yearsSince(entryYear, today));
    }

    public boolean isGradeManual() {
        return gradeAdjust != 0;
    }

    /** 학년도는 3월에 시작하므로 1·2월은 전년도로 본다. */
    private static int yearsSince(int entryYear, LocalDate today) {
        int academicYear = today.getMonthValue() >= 3 ? today.getYear() : today.getYear() - 1;
        return academicYear - entryYear + 1;
    }

    /** 5학년 이상은 4로 본다. */
    private static int clamp(int grade) {
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
