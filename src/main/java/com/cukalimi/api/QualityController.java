package com.cukalimi.api;

import com.cukalimi.domain.DateItem;
import com.cukalimi.domain.DateItemRepository;
import com.cukalimi.domain.DateReportRepository;
import com.cukalimi.domain.ImageTranscriptRepository;
import com.cukalimi.domain.ReviewEvent;
import com.cukalimi.domain.ReviewEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 품질 페이지의 수치 (ADR 011). 누구나 볼 수 있고 개인 정보는 없다.
 * backtest는 정답셋 백테스트의 마지막 결과다. 백테스트 테스트가 이 파일이 실제 결과와 같은지 확인한다.
 * review와 transcription은 운영 중에 쌓인 검수 기록과 그림 받아 적기 기록을 센 것이다.
 * 로그인 없이 누구나 부를 수 있어 결과를 1분 동안 재사용한다. 그림 기록은 합계 쿼리로만 센다.
 */
@RestController
@RequestMapping("/api")
public class QualityController {

    /** claude-haiku-5-5 단가, 달러 / 100만 토큰 (2026-10 기준). */
    static final double PRICE_IN = 0.10;
    static final double PRICE_OUT = 0.50;

    private final ReviewEventRepository reviewEvents;
    private final ImageTranscriptRepository transcripts;
    private final DateItemRepository dateItems;
    private final DateReportRepository reports;
    private final ObjectMapper json;
    private final JsonNode backtest;
    private volatile Quality cached;
    private volatile long cachedAt;
    static final long CACHE_MILLIS = 60_000;

    public QualityController(ReviewEventRepository reviewEvents, ImageTranscriptRepository transcripts,
            DateItemRepository dateItems, DateReportRepository reports, ObjectMapper json) {
        this.dateItems = dateItems;
        this.reports = reports;
        this.reviewEvents = reviewEvents;
        this.transcripts = transcripts;
        this.json = json;
        try (InputStream in = getClass().getResourceAsStream("/quality/extraction.json")) {
            this.backtest = json.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 자동으로 뽑은 항목에 운영자가 내린 첫 판단. 그대로 승인, 고침, 지움으로 나눈다. added는 자동 추출이 놓쳐 손으로 넣은 항목이다.
     * fromImage는 그 가운데 그림에서 읽은 후보만 센 것이다.
     */
    public record Review(int judged, int approvedAsIs, int edited, int deleted, int added, int fromImageJudged,
            int fromImageApprovedAsIs, int noticesReviewed) {
    }

    /** images는 받아 적은 횟수(그림 한 장을 두 번 읽는다), skipped는 형식·크기·주소 때문에 건너뛰었거나 실패한 수. */
    public record Transcription(int images, int skipped, long inputTokens, long outputTokens, double costUsd) {
    }

    /** published는 두 번 읽기가 일치해 지금 공개 중인 그림 날짜 수, waiting은 어긋나 운영자를 기다리는 수, reports는 학생 신고 수. */
    public record AutoPublish(long published, long waiting, long reports) {
    }

    public record Quality(JsonNode backtest, Review review, Transcription transcription, AutoPublish autoPublish) {
    }

    @GetMapping("/quality")
    @Transactional(readOnly = true)
    public Quality quality() {
        long now = System.currentTimeMillis();
        Quality q = cached;
        if (q == null || now - cachedAt > CACHE_MILLIS) {
            q = new Quality(backtest, review(), transcription(), new AutoPublish(dateItems.countByStatus(DateItem.STATUS_AUTO_IMAGE),
                    dateItems.countByStatus(DateItem.STATUS_CANDIDATE), reports.count()));
            cached = q;
            cachedAt = now;
        }
        return q;
    }

    private Review review() {
        int approved = 0;
        int edited = 0;
        int deleted = 0;
        int added = 0;
        int imageJudged = 0;
        int imageApproved = 0;
        Set<Long> judgedItems = new HashSet<>();
        Set<Long> reviewedNotices = new HashSet<>();
        for (ReviewEvent e : reviewEvents.findAll(org.springframework.data.domain.Sort.by("id"))) {
            if (e.getDateItemId() == null) {
                reviewedNotices.add(e.getNoticeId());
                continue;
            }
            if (ReviewEvent.ADD.equals(e.getAction())) {
                added++;
                continue;
            }
            String before = beforeStatus(e);
            boolean automatic = DateItem.STATUS_AUTO.equals(before) || DateItem.STATUS_CANDIDATE.equals(before)
                    || DateItem.STATUS_AUTO_IMAGE.equals(before);
            // 자동 항목마다 첫 판단만 센다. 사람이 고친 뒤의 수정은 추출 정확도와 상관없다.
            if (!automatic || !judgedItems.add(e.getDateItemId())) {
                continue;
            }
            boolean image = DateItem.STATUS_CANDIDATE.equals(before) || DateItem.STATUS_AUTO_IMAGE.equals(before);
            switch (e.getAction()) {
                case ReviewEvent.APPROVE -> {
                    approved++;
                    imageApproved += image ? 1 : 0;
                }
                case ReviewEvent.EDIT -> edited++;
                case ReviewEvent.DELETE -> deleted++;
                default -> {
                }
            }
            imageJudged += image ? 1 : 0;
        }
        return new Review(approved + edited + deleted, approved, edited, deleted, added, imageJudged, imageApproved,
                reviewedNotices.size());
    }

    private String beforeStatus(ReviewEvent e) {
        try {
            return e.getBeforeJson() == null ? null : json.readTree(e.getBeforeJson()).path("status").asText(null);
        } catch (IOException ex) {
            return null;
        }
    }

    private Transcription transcription() {
        ImageTranscriptRepository.Usage u = transcripts.usage();
        double cost = u.getInputTokens() / 1e6 * PRICE_IN + u.getOutputTokens() / 1e6 * PRICE_OUT;
        return new Transcription((int) u.getImages(), (int) transcripts.countByInputTokens(0), u.getInputTokens(),
                u.getOutputTokens(), Math.round(cost * 10000) / 10000.0);
    }
}
