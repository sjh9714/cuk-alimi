package com.cukalimi.schedule;

import com.cukalimi.config.AppProperties;
import com.cukalimi.fetch.PageFetcher;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 학사일정 표를 하루 한 번 읽어 date_item으로 쌓는다. 읽기는 트랜잭션 밖, 저장은 ScheduleStore가 한 트랜잭션으로. */
@Service
public class ScheduleCollector {

    private static final Logger log = LoggerFactory.getLogger(ScheduleCollector.class);

    private final AppProperties props;
    private final PageFetcher fetcher;
    private final ScheduleStore store;

    public ScheduleCollector(AppProperties props, PageFetcher fetcher, ScheduleStore store) {
        this.props = props;
        this.fetcher = fetcher;
        this.store = store;
    }

    @Scheduled(cron = "${app.schedule.cron}", zone = "Asia/Seoul")
    public void collectScheduled() {
        for (int year : props.schedule().years()) {
            try {
                ScheduleStore.Result r = collect(year);
                log.info("schedule {} seen={} kept={} inserted={} updated={} removed={}", year, r.seen(), r.kept(),
                        r.inserted(), r.updated(), r.removed());
            } catch (RuntimeException e) {
                log.error("schedule {} failed", year, e);
            }
        }
    }

    public ScheduleStore.Result collect(int academicYear) {
        String html = fetcher.get(props.scheduleUrl(academicYear));
        List<ScheduleEntry> entries = ScheduleParser.parse(html, academicYear);
        return store.apply(academicYear, props.schedule().campus(), entries);
    }
}
