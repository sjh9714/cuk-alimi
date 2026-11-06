package com.cukalimi.quality;

import static org.assertj.core.api.Assertions.assertThat;

import com.cukalimi.Fixtures;
import com.cukalimi.feed.ItemKind;
import com.cukalimi.feed.Topic;
import com.cukalimi.notice.DateItemExtractor;
import com.cukalimi.notice.ExtractedItem;
import com.cukalimi.notice.NoticeView;
import com.cukalimi.notice.NoticeViewParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * 처음 보는 공지 점수 (2026-11-06). 규칙을 고칠 때 한 번도 보지 않은 공지 26건(대부분 컴퓨터정보공학부 게시판)에
 * 정답을 먼저 적고(gold/fresh.json), 지금 규칙을 돌려 마감·행 제목·주제를 잰다. 학습 셋 점수 옆에 나란히 공개한다.
 * 이 셋을 보고 규칙을 고치면 이 점수는 의미를 잃는다. 기준선은 걸지 않고, 공개 요약(quality/fresh.json)과 같은지만 본다.
 */
class FreshSetTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void freshNoticeScoresArePublished() throws Exception {
        JsonNode gold = JSON.readTree(Fixtures.read("gold/fresh.json")).get("notices");
        int tp = 0, fp = 0, fn = 0, labelTotal = 0, labelExact = 0, topicTotal = 0, topicExact = 0, exactNotices = 0;
        List<String> report = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = gold.fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> e = it.next();
            NoticeView view = NoticeViewParser.parse(Fixtures.read("fresh/" + e.getKey() + ".html"));
            List<ExtractedItem> items = DateItemExtractor.extract(view);
            List<ExtractedItem> actions = items.stream().filter(i -> ItemKind.ACTION.equals(ItemKind.of(i.title()))).toList();
            Set<LocalDate> got = actions.stream().map(i -> i.range().end()).collect(Collectors.toCollection(TreeSet::new));
            Set<LocalDate> want = new TreeSet<>();
            e.getValue().get("deadlines").forEach(d -> want.add(LocalDate.parse(d.get("date").asText())));
            int hit = (int) want.stream().filter(got::contains).count();
            tp += hit;
            fn += want.size() - hit;
            fp += (int) got.stream().filter(d -> !want.contains(d)).count();
            if (got.equals(want)) {
                exactNotices++;
            } else {
                report.add(e.getKey() + " 정답 " + want + " 결과 " + got + " | " + view.title());
            }
            for (JsonNode d : e.getValue().get("deadlines")) {
                LocalDate day = LocalDate.parse(d.get("date").asText());
                String expected = d.get("label").asText();
                labelTotal++;
                List<String> titles = actions.stream().filter(i -> i.range().end().equals(day))
                        .map(i -> i.title().equals(view.cleanTitle()) ? "" : i.title()).toList();
                if (titles.contains(expected)) {
                    labelExact++;
                } else {
                    report.add(e.getKey() + " " + day + " 라벨 정답 \"" + expected + "\" 결과 " + titles);
                }
            }
            topicTotal++;
            String topic = Topic.of(view.title()).key();
            if (topic.equals(e.getValue().get("topic").asText())) {
                topicExact++;
            } else {
                report.add(e.getKey() + " 주제 정답 " + e.getValue().get("topic").asText() + " 규칙 " + topic + " | " + view.title());
            }
        }
        Files.createDirectories(Path.of("build/reports/fresh"));
        Files.writeString(Path.of("build/reports/fresh/misses.txt"), String.join("\n", report) + "\n");
        ObjectNode s = JSON.createObjectNode();
        s.put("notices", topicTotal).put("deadlines", tp + fn).put("exactNotices", exactNotices)
                .put("precision", round(tp, tp + fp)).put("recall", round(tp, tp + fn))
                .put("labelExact", labelExact).put("labelTotal", labelTotal)
                .put("topicExact", topicExact).put("topicTotal", topicTotal);
        System.out.println("fresh set: " + s);
        Path published = Path.of("src/main/resources/quality/fresh.json");
        if (System.getenv("QUALITY_UPDATE") != null || !Files.exists(published)) {
            JSON.writerWithDefaultPrettyPrinter().writeValue(published.toFile(), s);
        }
        assertThat(JSON.readTree(published.toFile())).as("공개 새 공지 요약이 이번 결과와 다르다. QUALITY_UPDATE=1로 다시 쓴다").isEqualTo(s);
    }

    private static double round(int a, int b) {
        return b == 0 ? 1.0 : Math.round(a * 1000.0 / b) / 1000.0;
    }
}
