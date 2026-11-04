package com.cukalimi;

import com.cukalimi.config.AppProperties;
import com.cukalimi.notice.NoticeCollector;
import com.cukalimi.schedule.ScheduleCollector;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * RUN_ONCE=schedule|notice|transcribe|all|digest|recheck|reparse|audit 로 띄우면 수집(또는 아침 알림)을 한 번 돌리고 종료한다.
 * 운영에서는 비워 두고 스케줄러에 맡긴다.
 */
@Component
@Order(10)
public class RunOnceRunner implements ApplicationRunner {

    private final AppProperties props;
    private final ScheduleCollector schedules;
    private final NoticeCollector noticesCollector;
    private final ConfigurableApplicationContext context;
    private final com.cukalimi.digest.DigestService digest;
    private final com.cukalimi.notice.ImageNoticeReader imageReader;
    private final com.cukalimi.domain.NoticeRepository notices;
    private final com.cukalimi.domain.NoticeRawRepository raws;
    private final com.cukalimi.notice.NoticeStore store;
    private final com.cukalimi.feed.FeedAudit audit;

    public RunOnceRunner(AppProperties props, ScheduleCollector schedules, NoticeCollector noticesCollector,
            ConfigurableApplicationContext context, com.cukalimi.digest.DigestService digest,
            com.cukalimi.notice.ImageNoticeReader imageReader, com.cukalimi.domain.NoticeRepository notices,
            com.cukalimi.domain.NoticeRawRepository raws, com.cukalimi.notice.NoticeStore store, com.cukalimi.feed.FeedAudit audit) {
        this.audit = audit;
        this.notices = notices;
        this.raws = raws;
        this.store = store;
        this.imageReader = imageReader;
        this.props = props;
        this.schedules = schedules;
        this.noticesCollector = noticesCollector;
        this.context = context;
        this.digest = digest;
    }

    @Override
    public void run(ApplicationArguments args) {
        String mode = props.runOnce() == null ? "" : props.runOnce().trim();
        if (mode.isEmpty()) {
            return;
        }
        int exit = 0;
        try {
            if (mode.equals("schedule") || mode.equals("all")) {
                for (int year : props.schedule().years()) {
                    System.out.println("schedule " + year + " " + schedules.collect(year));
                }
            }
            if (mode.equals("notice") || mode.equals("all")) {
                System.out.println("notice " + noticesCollector.collectAll());
            }
            if (mode.equals("transcribe") || mode.equals("all")) {
                System.out.println("transcribe " + imageReader.readPending(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))));
            }
            if (mode.equals("reparse")) {
                // 추출 규칙을 고친 뒤 저장해 둔 원문으로 최근 90일 공지의 날짜 항목을 다시 뽑는다. 학교에 요청하지 않는다.
                int n = 0;
                int failed = 0;
                for (var notice : notices.findByPostedOnGreaterThanEqualAndRemovedAtIsNull(
                        java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).minusDays(90))) {
                    var raw = raws.findById(notice.getArticleNo()).orElse(null);
                    if (raw == null) {
                        continue;
                    }
                    try {
                        store.save(new com.cukalimi.notice.NoticeSummary(notice.getArticleNo(), notice.getCategory(), notice.getTitle(),
                                notice.getPostedOn(), notice.getUrl(), notice.getDept()), raw.getHtml());
                        n++;
                    } catch (RuntimeException e) {
                        // 읽기 실패로 남은 원문("점검 중")은 본문이 없다. 그 공지는 건너뛴다.
                        failed++;
                    }
                }
                System.out.println("reparse notices=" + n + " failed=" + failed);
            }
            if (mode.equals("audit")) {
                var findings = audit.run(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")));
                System.out.println("audit findings=" + findings.size());
                findings.forEach(f -> System.out.println("audit " + f));
            }
            if (mode.equals("recheck")) {
                System.out.println("recheck changed=" + noticesCollector.recheckOpenNotices(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))));
            }
            if (mode.equals("digest")) {
                System.out.println("digest " + digest.sendAll());
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
            exit = 1;
        }
        System.exit(SpringApplication.exit(context, () -> 0) + exit);
    }
}
