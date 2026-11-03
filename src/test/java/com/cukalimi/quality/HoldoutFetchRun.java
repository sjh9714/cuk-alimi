package com.cukalimi.quality;

import com.cukalimi.notice.NoticeListParser;
import com.cukalimi.notice.NoticeSummary;
import com.cukalimi.notice.NoticeView;
import com.cukalimi.notice.NoticeViewParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 형광펜 시험용 셋(holdout)을 모은다 (ADR 013). 개발용 62건(notices-2026-10-26.json)과 겹치지 않는 장학·학사 공지를
 * 학교 게시판의 뒤쪽 페이지에서 받는다. 운영 수집과 같은 1.5초 간격과 사용자 에이전트를 쓴다. 평소 테스트에서는 돌지 않는다.
 *
 * <pre>HOLDOUT_FETCH=1 ./gradlew test --tests '*HoldoutFetchRun' --rerun</pre>
 */
@EnabledIfEnvironmentVariable(named = "HOLDOUT_FETCH", matches = "1")
class HoldoutFetchRun {

    static final Path OUT = Path.of("src/test/resources/fixtures/notices-holdout-2026-11-03.json");
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final String BASE = "https://www.catholic.ac.kr";
    private static final String USER_AGENT = "cuk-alimi/0.1 (student notice collector; contact in README)";
    private static final int PER_CATEGORY = 35;
    private long last;

    @Test
    void run() throws Exception {
        Set<Long> dev = new HashSet<>();
        for (JsonNode n : JSON.readTree(HoldoutFetchRun.class.getResourceAsStream("/fixtures/notices-2026-10-26.json"))) {
            dev.add(n.get("articleNo").asLong());
        }
        ArrayNode out = JSON.createArrayNode();
        for (Map.Entry<String, Integer> category : Map.of("학사", 21, "장학", 22).entrySet()) {
            int taken = 0;
            // 첫 페이지는 개발용 셋과 겹친다. 둘째 페이지부터 받는다.
            for (int offset = 30; taken < PER_CATEGORY && offset <= 150; offset += 30) {
                List<NoticeSummary> list = NoticeListParser.parse(get(BASE + "/ko/campuslife/notice.do?mode=list&srCategoryId="
                        + category.getValue() + "&articleLimit=30&article.offset=" + offset));
                for (NoticeSummary s : list) {
                    if (taken >= PER_CATEGORY || dev.contains(s.articleNo())) {
                        continue;
                    }
                    NoticeView v = NoticeViewParser.parse(get(BASE + "/ko/campuslife/notice.do?mode=view&articleNo=" + s.articleNo()));
                    out.addObject().put("articleNo", s.articleNo()).put("category", category.getKey())
                            .put("title", v.title()).put("body", v.bodyText() == null ? "" : v.bodyText());
                    dev.add(s.articleNo());
                    taken++;
                }
            }
            System.out.printf("HOLDOUT %s %d%n", category.getKey(), taken);
        }
        JSON.writeValue(OUT.toFile(), out);
    }

    private String get(String url) throws Exception {
        long wait = last + 1500 - System.currentTimeMillis();
        if (wait > 0) {
            Thread.sleep(wait);
        }
        try {
            return Jsoup.connect(url).userAgent(USER_AGENT).timeout(15_000).execute().body();
        } finally {
            last = System.currentTimeMillis();
        }
    }
}
