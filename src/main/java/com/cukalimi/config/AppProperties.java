package com.cukalimi.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String baseUrl,
        long fetchIntervalMs,
        String runOnce,
        String publicUrl,
        String apiUrl,
        String adminEmail,
        boolean cookieSecure,
        Mail mail,
        Digest digest,
        Schedule schedule,
        Notice notice,
        Transcribe transcribe) {

    public record Schedule(String cron, String campus, List<Integer> years) {
    }

    /** 아침 알림 메일 시각. */
    public record Digest(String cron) {
    }

    public record Notice(String cron, int pageSize, List<Category> categories) {
    }

    /** 공지 게시판 분류. 학교 페이지의 srCategoryId 값과 짝이다. */
    public record Category(String name, int id) {
    }

    /**
     * 그림 공지 받아 적기 (ADR 010). apiKey가 비면 꺼진다. dailyLimit은 하루에 새로 받아 적는 그림 수의 상한이다.
     * toString에 키가 찍히지 않게 한다.
     */
    public record Transcribe(String apiKey, String model, int dailyLimit, String cron) {
        public boolean enabled() {
            return apiKey != null && !apiKey.isBlank();
        }

        @Override
        public String toString() {
            return "Transcribe[enabled=" + enabled() + ", model=" + model + ", dailyLimit=" + dailyLimit + "]";
        }
    }

    /** mode는 console(링크를 로그에 찍음) 또는 smtp. */
    public record Mail(String mode, String from) {
    }

    public String noticeListUrl(int categoryId) {
        return baseUrl + "/ko/campuslife/notice.do?mode=list&srCategoryId=" + categoryId
                + "&articleLimit=" + notice.pageSize();
    }

    public String noticeViewUrl(long articleNo) {
        return baseUrl + "/ko/campuslife/notice.do?mode=view&articleNo=" + articleNo;
    }

    public String scheduleUrl(int year) {
        return baseUrl + "/ko/support/calendar2024_list.do?mode=list&siteId=ko&selectedYear=" + year;
    }

    /** 메일 링크가 가리킬 API 주소. 운영은 화면과 같은 origin, 로컬 개발은 8080. */
    public String apiUrlOrPublic() {
        return apiUrl == null || apiUrl.isBlank() ? publicUrl : apiUrl;
    }

    public boolean isAdmin(String email) {
        return adminEmail != null && !adminEmail.isBlank() && adminEmail.equalsIgnoreCase(email);
    }
}
