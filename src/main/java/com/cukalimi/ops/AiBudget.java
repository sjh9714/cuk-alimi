package com.cukalimi.ops;

import com.cukalimi.config.AppProperties;
import com.cukalimi.domain.ImageTranscriptRepository;
import com.cukalimi.domain.NoticeRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 이번 달 AI 비용 (ADR 016). 저장해 둔 토큰 수에 단가를 곱해 센다. 상한(app.ai.monthly-budget-usd)에 닿으면 AI 작업이 모두 멈춘다.
 * 그림 받아 적기, 대상 읽기, 문장 찾기가 일을 시작하기 전에 allow()를 묻는다. 정답 녹음 도구처럼 DB에 남지 않는 호출은 세지 못하므로
 * 콘솔의 월 한도를 함께 건다.
 */
@Service
public class AiBudget {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** 모델 하나가 쓴 토큰. */
    public interface Usage {
        String getModel();

        long getInputTokens();

        long getOutputTokens();
    }

    private final AppProperties props;
    private final ImageTranscriptRepository transcripts;
    private final NoticeRepository notices;
    private final Clock clock;

    public AiBudget(AppProperties props, ImageTranscriptRepository transcripts, NoticeRepository notices, Clock clock) {
        this.props = props;
        this.transcripts = transcripts;
        this.notices = notices;
        this.clock = clock;
    }

    public double limitUsd() {
        return props.ai() == null ? 0 : props.ai().monthlyBudgetUsd();
    }

    /** 이번 달(서울 기준 1일 0시부터) 쓴 돈. */
    public double monthToDateUsd() {
        OffsetDateTime since = LocalDate.now(clock.withZone(SEOUL)).withDayOfMonth(1).atStartOfDay(SEOUL).toOffsetDateTime();
        return usd(transcripts.usageSince(since)) + usd(notices.audienceUsageSince(since));
    }

    /** 더 써도 되는가. 상한이 0 이하면 가드를 끈 것이다. */
    public boolean allow() {
        return allowed(monthToDateUsd(), limitUsd());
    }

    static boolean allowed(double spent, double limit) {
        return limit <= 0 || spent < limit;
    }

    static double usd(List<? extends Usage> usage) {
        return usage.stream().mapToDouble(u -> AiPrices.usd(u.getModel(), u.getInputTokens(), u.getOutputTokens())).sum();
    }
}
