package com.cukalimi.quality;

import static org.assertj.core.api.Assertions.assertThat;

import com.cukalimi.Fixtures;
import com.cukalimi.domain.DateItem;
import com.cukalimi.domain.Notice;
import com.cukalimi.feed.Audience;
import com.cukalimi.feed.Feed;
import com.cukalimi.feed.GradeTag;
import com.cukalimi.notice.DateItemExtractor;
import com.cukalimi.notice.ExtractedItem;
import com.cukalimi.notice.ImageNoticeReader;
import com.cukalimi.notice.NoticeView;
import com.cukalimi.notice.NoticeViewParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 행 제목 채점에 쓰는 카드. 정답셋 공지를 실제 피드 코드(Feed.build)에 넣어 API가 내보내는 것과 같은 카드를 만든다.
 * 웹 시험(web/src/lib/rows.test.ts)이 이 카드로 화면 행을 만들고 gold/rows.json과 비교한다.
 * 카드 파일이 이번 결과와 다르면 실패한다. 추출기나 피드를 고쳤으면 QUALITY_UPDATE=1로 다시 쓰고 웹 시험을 돌린다.
 */
class RowCardsTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private static final Path OUT = Path.of("web/src/lib/__fixtures__/gold-cards.json");

    @Test
    void goldCardsMatchTheFeedCode() throws Exception {
        JsonNode labels = JSON.readTree(Fixtures.read("gold/labels.json")).get("notices");
        Map<String, Object> out = new java.util.TreeMap<>();
        for (String no : ids("src/test/resources/fixtures/gold/notices")) {
            NoticeView view = NoticeViewParser.parse(Fixtures.read("gold/notices/" + no + ".html"));
            boolean image = "image".equals(labels.path(no).path("source").asText());
            List<ExtractedItem> items = image
                    ? ImageNoticeReader.extractFromTranscripts(view, List.of(Fixtures.read("gold/transcripts/" + no + ".html")))
                    : DateItemExtractor.extract(view);
            out.put(no, cards(view, items, image));
        }
        for (String no : ids("src/test/resources/fixtures/fresh")) {
            NoticeView view = NoticeViewParser.parse(Fixtures.read("fresh/" + no + ".html"));
            out.put(no, cards(view, DateItemExtractor.extract(view), false));
        }
        String now = JSON.writeValueAsString(out) + "\n";
        if (System.getenv("QUALITY_UPDATE") != null || !Files.exists(OUT)) {
            Files.createDirectories(OUT.getParent());
            Files.writeString(OUT, now);
            return;
        }
        assertThat(Files.readString(OUT)).as("정답셋 카드가 피드 코드와 다르다. QUALITY_UPDATE=1로 다시 쓰고 웹 시험(rows.test.ts)을 돌린다")
                .isEqualTo(now);
    }

    private static List<String> ids(String dir) throws Exception {
        try (Stream<Path> files = Files.list(Path.of(dir))) {
            return files.map(p -> p.getFileName().toString()).filter(f -> f.endsWith(".html"))
                    .map(f -> f.substring(0, f.length() - 5)).sorted().toList();
        }
    }

    /** 공지 하나를 피드에 넣고 나온 카드 전부. 구간 이름을 함께 남겨 웹이 접힌 구간도 똑같이 다루게 한다. */
    private static List<Map<String, Object>> cards(NoticeView view, List<ExtractedItem> extracted, boolean image) {
        long noticeId = view.articleNo();
        Notice notice = new Notice(view.articleNo(), view.category(), view.title(), view.postedOn(), "", "https://example.test/"
                + view.articleNo(), "", image, "", null);
        ReflectionTestUtils.setField(notice, "id", noticeId);
        List<DateItem> items = new ArrayList<>();
        Map<String, Integer> occurrences = new HashMap<>();
        long id = noticeId * 100;
        for (ExtractedItem e : extracted) {
            int occurrence = occurrences.merge(e.naturalKey(0, 0), 1, Integer::sum);
            DateItem item = DateItem.fromNotice(noticeId, e.naturalKey(view.articleNo(), occurrence), e.title(), e.range().start(),
                    e.range().end(), e.range().startsAt(), e.range().endsAt(), e.targetGrade());
            if (image) {
                ReflectionTestUtils.setField(item, "status", DateItem.STATUS_AUTO_IMAGE);
            }
            ReflectionTestUtils.setField(item, "id", ++id);
            items.add(item);
        }
        Audience general = new Audience(Audience.GENERAL, null, List.of(), null, GradeTag.NONE);
        Feed.View feed = Feed.build(items, Map.of(noticeId, notice), Map.of(noticeId, general), List.of(), Feed.Viewer.ANONYMOUS,
                false, Set.of(), Set.of(), y -> "");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Feed.Section s : feed.sections()) {
            for (Feed.Card c : s.cards()) {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("section", s.key());
                m.put("collapsed", s.collapsed());
                m.put("card", c);
                out.add(m);
            }
        }
        return out;
    }
}
