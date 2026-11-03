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
    /** 입학년도로 센 학기와 실제 학기의 차이. 휴학이나 군 복무로 늦어지면 음수다. */
    private int semesterAdjust;
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

    /** semester는 학생이 고른 이번 학기(몇 번째 학기인지, 9는 초과학기)다. null이면 입학년도로 센다. */
    public void updateProfile(Integer entryYear, Integer semester, String major, String secondMajor, String homeRegion,
            LocalDate today) {
        this.entryYear = entryYear;
        this.semesterAdjust = entryYear == null || semester == null ? 0 : semester - semestersSince(entryYear, today);
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

    /** 이번 학기(1~8, 9는 초과학기). 학생이 고른 차이를 입학년도로 센 값에 더해서 학기마다 하나씩 오른다. */
    public Integer semester(LocalDate today) {
        return entryYear == null ? null : clamp(semestersSince(entryYear, today) + semesterAdjust);
    }

    /** 휴학을 모르고 입학년도로만 센 학기. */
    public Integer autoSemester(LocalDate today) {
        return entryYear == null ? null : clamp(semestersSince(entryYear, today));
    }

    /** 지금 학년. 학기에서 나온다. 초과학기는 4학년이다. */
    public Integer grade(LocalDate today) {
        Integer s = semester(today);
        return s == null ? null : gradeOf(s);
    }

    public boolean isSemesterManual() {
        return semesterAdjust != 0;
    }

    public static int gradeOf(int semester) {
        return Math.min(4, (semester + 1) / 2);
    }

    /** 1학기는 3~8월, 2학기는 9월~다음 해 2월이다. 입학한 해 1학기가 1번째 학기다. */
    private static int semestersSince(int entryYear, LocalDate today) {
        int month = today.getMonthValue();
        int academicYear = month >= 3 ? today.getYear() : today.getYear() - 1;
        boolean second = month >= 9 || month <= 2;
        return (academicYear - entryYear) * 2 + (second ? 2 : 1);
    }

    /** 9번째 학기 이상은 초과학기 하나로 본다. */
    private static int clamp(int semester) {
        return Math.max(1, Math.min(9, semester));
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
