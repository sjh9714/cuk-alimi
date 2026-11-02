package com.cukalimi.fetch;

import com.cukalimi.config.AppProperties;
import com.cukalimi.domain.FetchLog;
import com.cukalimi.domain.FetchLogRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

/** 학교 서버에 요청 간격을 지키며 페이지를 읽고, 요청마다 fetch_log를 남긴다. */
@Component
public class JsoupPageFetcher implements PageFetcher {

    private static final String USER_AGENT = "cuk-alimi/0.1 (student notice collector; contact in README)";

    private final AppProperties props;
    private final FetchLogRepository fetchLogs;
    private long lastRequestAt = 0;

    public JsoupPageFetcher(AppProperties props, FetchLogRepository fetchLogs) {
        this.props = props;
        this.fetchLogs = fetchLogs;
    }

    @Override
    public synchronized String get(String url) {
        waitForInterval();
        String source = url.contains("calendar") ? "SCHEDULE" : "NOTICE";
        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(15_000)
                    .execute();
            fetchLogs.save(FetchLog.ok(source, url, response.statusCode()));
            return response.body();
        } catch (IOException e) {
            fetchLogs.save(FetchLog.failed(source, url, e.getMessage()));
            throw new UncheckedIOException("fetch failed: " + url, e);
        } finally {
            lastRequestAt = System.currentTimeMillis();
        }
    }

    /** 공지 그림. 페이지와 같은 간격을 지키고 같은 기록을 남긴다. 5MB를 넘으면 잘라 읽으므로 호출하는 쪽이 크기를 본다. */
    @Override
    public synchronized byte[] getBytes(String url) {
        waitForInterval();
        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(30_000)
                    .ignoreContentType(true)
                    // 학교 주소인지 확인한 뒤에 부른다. 다른 곳으로 넘겨도 따라가지 않는다.
                    .followRedirects(false)
                    .maxBodySize(MAX_IMAGE_BYTES + 1)
                    .execute();
            // 넘기는 응답(3xx)은 따라가지 않으므로 예외 없이 돌아온다. 그림이 아니니 실패로 다룬다.
            if (response.statusCode() / 100 != 2) {
                fetchLogs.save(FetchLog.failed("IMAGE", url, "status " + response.statusCode()));
                throw new UncheckedIOException(new org.jsoup.HttpStatusException("not an image response",
                        response.statusCode(), url));
            }
            fetchLogs.save(FetchLog.ok("IMAGE", url, response.statusCode()));
            return response.bodyAsBytes();
        } catch (IOException e) {
            fetchLogs.save(FetchLog.failed("IMAGE", url, e.getMessage()));
            throw new UncheckedIOException("fetch failed: " + url, e);
        } finally {
            lastRequestAt = System.currentTimeMillis();
        }
    }

    /** Claude API가 받는 그림 한 장의 최대 크기. */
    public static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    private void waitForInterval() {
        long wait = lastRequestAt + props.fetchIntervalMs() - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
