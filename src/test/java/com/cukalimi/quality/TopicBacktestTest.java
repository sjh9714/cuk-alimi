package com.cukalimi.quality;

import static org.assertj.core.api.Assertions.assertThat;

import com.cukalimi.Fixtures;
import com.cukalimi.feed.Topic;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * 공지 주제 규칙의 정확도 (ADR 014). 정답(gold/topics.json)은 규칙을 보지 않은 모델이 제목만 읽고 붙인 고정 시험 세트다.
 * 공지마다 계속 라벨을 붙이지 않는다. 규칙을 고칠 때 이 수치가 기준선 밑으로 떨어지면 실패한다. 공개 요약은 quality/topics.json이다.
 */
class TopicBacktestTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    /** 2026-11-04 규칙을 이 정답으로 다듬은 뒤의 값(0.966)에서 내림. 같은 셋을 보며 고쳤으니 학습 셋 점수다. 올라가면 올린다. */
    static final double BASELINE = 0.96;

    @Test
    void topicRulesDoNotRegress() throws Exception {
        JsonNode gold = JSON.readTree(Fixtures.read("gold/topics.json"));
        int total = 0;
        int hit = 0;
        Map<String, int[]> perTopic = new TreeMap<>();
        List<String> misses = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = gold.get("notices").fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> e = it.next();
            String want = e.getValue().get("topic").asText();
            String got = Topic.of(e.getValue().get("title").asText()).key();
            total++;
            int[] c = perTopic.computeIfAbsent(want, k -> new int[2]);
            c[0]++;
            if (want.equals(got)) {
                hit++;
                c[1]++;
            } else {
                misses.add(e.getKey() + " 정답 " + want + " 규칙 " + got + " | " + e.getValue().get("title").asText());
            }
        }
        double accuracy = Math.round(hit * 1000.0 / total) / 1000.0;
        Files.createDirectories(Path.of("build/reports/topics"));
        Files.writeString(Path.of("build/reports/topics/misses.txt"), String.join("\n", misses) + "\n");
        System.out.printf("topic backtest: %d/%d (%.3f)%n", hit, total, accuracy);

        ObjectNode summary = JSON.createObjectNode();
        summary.put("notices", total).put("exact", hit).put("accuracy", accuracy)
                .put("labeler", gold.path("labeler").asText()).put("humanChecked", gold.path("humanChecked").asInt());
        ObjectNode byTopic = summary.putObject("byTopic");
        perTopic.forEach((k, c) -> byTopic.putObject(k).put("notices", c[0]).put("exact", c[1]));
        Path published = Path.of("src/main/resources/quality/topics.json");
        if (System.getenv("QUALITY_UPDATE") != null || !Files.exists(published)) {
            JSON.writerWithDefaultPrettyPrinter().writeValue(published.toFile(), summary);
        }
        assertThat(JSON.readTree(published.toFile())).as("공개 주제 요약이 이번 결과와 다르다. QUALITY_UPDATE=1로 다시 쓴다").isEqualTo(summary);
        assertThat(accuracy).as("주제 규칙 정확도").isGreaterThanOrEqualTo(BASELINE);
    }
}
