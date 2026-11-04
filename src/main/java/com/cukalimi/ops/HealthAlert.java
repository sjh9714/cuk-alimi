package com.cukalimi.ops;

import com.cukalimi.auth.MailSender;
import com.cukalimi.config.AppProperties;
import com.cukalimi.domain.FetchLogRepository;
import com.cukalimi.domain.NoticeRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 수집이 멈췄는지 아침마다 본다 (2026-11-04 냉정한 평가 계획 10번). 학교 HTML이 바뀌거나 서버가 막히면 지금은 아무도 모른다.
 * 어제 하루 요청이 없거나, 절반 넘게 실패했거나, 사흘 동안 새 공지가 하나도 없으면 운영자에게 메일 한 통을 보낸다. 새 의존성은 없다.
 */
@Service
public class HealthAlert {

    private static final Logger log = LoggerFactory.getLogger(HealthAlert.class);
    static final Duration DAY = Duration.ofHours(24);
    static final Duration QUIET = Duration.ofDays(3);

    private final AppProperties props;
    private final FetchLogRepository fetchLogs;
    private final NoticeRepository notices;
    private final MailSender mail;
    private final Clock clock;

    public HealthAlert(AppProperties props, FetchLogRepository fetchLogs, NoticeRepository notices, MailSender mail, Clock clock) {
        this.props = props;
        this.fetchLogs = fetchLogs;
        this.notices = notices;
        this.mail = mail;
        this.clock = clock;
    }

    /** 문제가 있으면 문장 목록, 없으면 빈 목록. */
    public List<String> problems() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<String> out = new ArrayList<>();
        long fetched = fetchLogs.countByFetchedAtAfter(now.minus(DAY));
        long failed = fetchLogs.countByFetchedAtAfterAndErrorIsNotNull(now.minus(DAY));
        if (fetched == 0) {
            out.add("지난 24시간 동안 학교 사이트에 한 번도 요청하지 않았어요. 스케줄러가 멈췄을 수 있어요.");
        } else if (failed * 2 > fetched) {
            out.add("지난 24시간 요청 " + fetched + "건 중 " + failed + "건이 실패했어요. 학교 사이트가 막혔거나 주소가 바뀌었을 수 있어요.");
        }
        if (notices.countByFirstSeenAtAfter(now.minus(QUIET)) == 0) {
            out.add("사흘 동안 새 공지가 하나도 들어오지 않았어요. 목록 HTML이 바뀌었을 수 있어요.");
        }
        return out;
    }

    @Scheduled(cron = "${app.digest.cron}", zone = "Asia/Seoul")
    public void checkScheduled() {
        try {
            send();
        } catch (RuntimeException e) {
            log.error("health alert failed", e);
        }
    }

    public boolean send() {
        if (props.adminEmail() == null || props.adminEmail().isBlank()) {
            return false;
        }
        List<String> problems = problems();
        if (problems.isEmpty()) {
            return false;
        }
        mail.send(props.adminEmail(), "[CUK 알리미] 수집 점검 " + problems.size() + "건",
                String.join("\n", problems) + "\n\n로그: docker compose -f docker-compose.prod.yml logs --since 24h app\n");
        log.warn("health alert sent: {}", problems);
        return true;
    }
}
