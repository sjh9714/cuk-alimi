package com.cukalimi.feed;

import com.cukalimi.domain.Notice;
import com.cukalimi.domain.NoticeRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 홈 행의 자동 점검 (2026-11-04 점검 노트의 3번). 학생 신고 단추를 뺐으니 이상한 행은 이 표로만 본다.
 * 오늘 피드(로그인 전)를 훑어 제목이 길거나, 자리 이름·숫자 라벨이거나, 같은 공지 같은 날 줄이 셋 넘거나, 최근 지워진 공지를 찍는다.
 * RUN_ONCE=audit 로 돌리고 결과를 운영 일지에 적는다.
 */
@Service
public class FeedAudit {

    static final int TITLE_LIMIT = 36;
    static final Pattern JUNK_LABEL = Pattern.compile("^(구\\s*분|내\\s*용|일\\s*정|일\\s*자|비\\s*고|기\\s*간|순\\s*번|번\\s*호|항\\s*목|[\\d\\s./-]+)$");
    static final Pattern DESCRIPTION = Pattern.compile("[▶▷※:]|\\. ");
    /** 표에서 딸려 온 기호. "수상 심의 필요>** …", "●어학성적제출●", "ㆍ 참가신청"처럼 칸의 꾸밈이 라벨에 남은 것이다. */
    static final Pattern SYMBOL = Pattern.compile("[●○■□◆◇★☆•ㆍ*<>]");

    public record Finding(String kind, String date, String label, String notice) {
        @Override
        public String toString() {
            return kind + " | " + date + " | " + label + " | " + notice;
        }
    }

    private final FeedService feeds;
    private final NoticeRepository notices;

    public FeedAudit(FeedService feeds, NoticeRepository notices) {
        this.feeds = feeds;
        this.notices = notices;
    }

    public List<Finding> run(LocalDate today) {
        List<Finding> out = new ArrayList<>();
        Feed.View view = feeds.build(null, today).view();
        for (Feed.Section section : view.sections()) {
            for (Feed.Card card : section.cards()) {
                List<Feed.Line> lines = new ArrayList<>(card.next());
                if (card.deadline() != null) {
                    lines.add(0, card.deadline());
                }
                Map<LocalDate, Integer> perDay = new LinkedHashMap<>();
                for (Feed.Line line : lines) {
                    if (!"ACTION".equals(line.kind())) {
                        continue;
                    }
                    String label = line.label() == null ? "" : line.label();
                    String head = label.split("\\s*·\\s*", 2)[0].trim();
                    if (label.length() > TITLE_LIMIT) {
                        out.add(new Finding("긴 라벨", line.endsOn().toString(), label, card.sourceTitle()));
                    }
                    if (!head.isEmpty() && JUNK_LABEL.matcher(head).find()) {
                        out.add(new Finding("자리 이름·숫자 라벨", line.endsOn().toString(), label, card.sourceTitle()));
                    }
                    if (SYMBOL.matcher(label).find()) {
                        out.add(new Finding("기호가 든 라벨", line.endsOn().toString(), label, card.sourceTitle()));
                    }
                    if (DESCRIPTION.matcher(label).find()) {
                        out.add(new Finding("설명이 붙은 라벨", line.endsOn().toString(), label, card.sourceTitle()));
                    }
                    perDay.merge(line.endsOn(), 1, Integer::sum);
                }
                // 학점교류(대학마다 한 줄)와 추천채용(추천 마감 표현이 여럿)은 같은 날 여러 줄이 정상이다.
                boolean manyByDesign = card.sourceTitle().contains("학점교류") || card.sourceTitle().contains("추천");
                perDay.forEach((day, n) -> {
                    if (n >= 3 && !manyByDesign) {
                        out.add(new Finding("같은 날 " + n + "줄", day.toString(), "", card.sourceTitle()));
                    }
                });
            }
        }
        // 주제 규칙이 못 나눈 최근 공지 (ADR 014). 10%를 넘으면 규칙을 하나 더한다.
        List<Notice> recent = notices.findByPostedOnGreaterThanEqualAndRemovedAtIsNull(today.minusDays(30));
        long other = recent.stream().filter(n -> Topic.of(n.getTitle()) == Topic.OTHER).count();
        if (!recent.isEmpty() && other * 10 > recent.size()) {
            out.add(new Finding("주제 기타 " + other + "/" + recent.size(), today.toString(), "", "최근 30일 공지의 10% 넘게 주제를 못 나눔"));
        }
        recent.stream().filter(n -> Topic.of(n.getTitle()) == Topic.OTHER).limit(10)
                .forEach(n -> out.add(new Finding("주제 기타", n.getPostedOn().toString(), "", n.getTitle())));
        OffsetDateTime weekAgo = OffsetDateTime.now().minusDays(7);
        for (Notice n : notices.findAll()) {
            if (n.isRemoved() && n.getRemovedAt() != null && n.getRemovedAt().isAfter(weekAgo)) {
                out.add(new Finding("최근 내린 공지 (" + n.getRemovedReason() + ")", n.getPostedOn().toString(), String.valueOf(n.getArticleNo()),
                        n.getTitle()));
            }
        }
        return out;
    }
}
