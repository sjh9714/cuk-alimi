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
        Transcribe transcribe,
        AudienceRead audienceRead,
        Push push,
        Ai ai,
        Locator locator) {

    /**
     * 규칙이 놓친 마감 문장을 AI가 찾는다 (ADR 016). model이 공지마다 두 번 읽고, 그 답이 화면을 바꾸려 할 때만 adjudicatorModel이 한 번 읽어 확인한다.
     * 키는 transcribe.apiKey를 같이 쓴다.
     */
    public record Locator(boolean enabled, String model, String adjudicatorModel, int dailyLimit, int adjudicatorDailyLimit, String cron) {
    }

    /** AI 전체에 거는 월 지출 상한, 달러 (ADR 016). 닿으면 AI 작업이 모두 멈춘다. 0 이하면 가드를 끈다. */
    public record Ai(double monthlyBudgetUsd) {
    }

    /**
     * 웹 푸시 (ADR 004). VAPID 키 쌍이 없으면 꺼진다. subject는 푸시 서비스가 문제 있을 때 연락할 mailto 주소다.
     * toString에 비밀 키가 찍히지 않게 한다.
     */
    public record Push(String publicKey, String privateKey, String subject, String cron) {
        public boolean enabled() {
            return publicKey != null && !publicKey.isBlank() && privateKey != null && !privateKey.isBlank();
        }

        @Override
        public String toString() {
            return "Push[enabled=" + enabled() + "]";
        }
    }

    /**
     * AI가 공지 글에서 대상 조건을 읽는다 (ADR 013). 키는 transcribe와 같은 것을 쓴다. dailyLimit은 하루에 읽는 공지 수다.
     * 공지 한 건을 두 번 읽으니 호출 수로는 두 배다.
     */
    public record AudienceRead(boolean enabled, String model, int dailyLimit, String cron) {
    }

    public record Schedule(String cron, String campus, List<Integer> years) {
    }

    /** 아침 알림 메일 시각. */
    public record Digest(String cron) {
    }

    public record Notice(String cron, int pageSize, List<Category> categories, List<Board> boards) {
        public List<Board> boardsOrEmpty() {
            return boards == null ? List.of() : boards;
        }
    }

    /** 학과 게시판 하나 (ADR 002 추가). 같은 CMS라 글 번호는 중앙 게시판과 겹치지 않는다. viewUrl 뒤에 글 번호를 붙인다. */
    public record Board(String name, String dept, String listUrl, String viewUrl) {
        public String viewUrl(long articleNo) {
            return viewUrl + articleNo;
        }
    }

    /** 공지 게시판 분류. 학교 페이지의 srCategoryId 값과 짝이다. */
    public record Category(String name, int id) {
    }

    /**
     * 그림 공지 받아 적기 (ADR 010). apiKey가 비거나 enabled가 false면 꺼진다. dailyLimit은 하루 호출 수의 상한이다. 그림 한 장을 두 번 읽으니 그림 수로는 절반이다.
     * 키는 글을 읽는 AI(대상 읽기, 문장 찾기)도 함께 쓴다. 그림만 끄고 글 AI는 켜 둘 수 있게 스위치를 따로 둔다 (ADR 016).
     * 학교 robots.txt가 그림 경로를 막고 있어 그림만 끄고 배포할 수 있어야 한다. toString에 키가 찍히지 않게 한다.
     */
    public record Transcribe(String apiKey, String model, int dailyLimit, String cron, boolean enabled) {
        /** AI 키가 있는가. 글을 읽는 AI는 이것만 본다. */
        public boolean hasKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        @Override
        public boolean enabled() {
            return enabled && hasKey();
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
