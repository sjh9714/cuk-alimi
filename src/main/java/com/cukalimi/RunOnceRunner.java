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
 * RUN_ONCE=schedule|notice|transcribe|all|digest 로 띄우면 수집(또는 아침 알림)을 한 번 돌리고 종료한다.
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

    public RunOnceRunner(AppProperties props, ScheduleCollector schedules, NoticeCollector noticesCollector,
            ConfigurableApplicationContext context, com.cukalimi.digest.DigestService digest,
            com.cukalimi.notice.ImageNoticeReader imageReader) {
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
