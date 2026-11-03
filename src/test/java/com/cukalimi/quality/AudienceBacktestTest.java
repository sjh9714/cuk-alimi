package com.cukalimi.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.cukalimi.Fixtures;
import com.cukalimi.feed.Audience;
import com.cukalimi.feed.AudienceFacts;
import com.cukalimi.feed.Feed;
import com.cukalimi.feed.Regions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 형광펜 백테스트 (ADR 013). 공지 62건마다 정답(Opus가 읽은 대상 조건)으로 칠해야 할 학생과, 정규식과 AI(Haiku 두 번 읽기)가 실제로 칠하는 학생을 비교한다.
 * 학생은 학년 1~4, 정답에 나온 학과와 지역, 아무 데도 해당하지 않는 학과와 지역 없음을 모두 조합해 만든다.
 * 칠한 것 중 맞은 비율(정밀도)과 칠해야 할 것 중 칠한 비율(재현율)을 낸다. AI가 기준선보다 떨어지면 실패한다.
 * Haiku 응답은 저장해 둔 것을 쓰므로 API를 부르지 않는다. 두 번 읽기가 어긋난 공지는 운영과 같이 정규식으로 돌아간다.
 * 공개 품질 페이지의 요약(src/main/resources/quality/audience.json)이 이번 결과와 다르면 실패한다. 수치가 바뀌면 QUALITY_UPDATE=1로 다시 쓴다.
 */
class AudienceBacktestTest {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final String OTHER_DEPT = "국어국문학과";

    /** expected는 정답이 칠한 (학생, 공지) 쌍, predicted는 방법이 칠한 쌍, hit은 둘 다 칠한 쌍. exact는 학생 전원에 대해 정답과 같게 칠한 공지 수. */
    static final class Score {
        int expected;
        int predicted;
        int hit;
        int exact;

        double precision() {
            return predicted == 0 ? 1.0 : (double) hit / predicted;
        }

        double recall() {
            return expected == 0 ? 1.0 : (double) hit / expected;
        }
    }

    @Test
    void highlightDoesNotRegress() throws Exception {
        assumeTrue(Files.exists(AudienceLabelRun.GOLD) && Files.exists(AudienceLabelRun.HAIKU_OUT), "정답지와 Haiku 기록이 아직 없다");
        JsonNode notices = JSON.readTree(Fixtures.read("notices-2026-10-26.json"));
        JsonNode gold = JSON.readTree(AudienceLabelRun.GOLD.toFile());
        JsonNode haiku = JSON.readTree(AudienceLabelRun.HAIKU_OUT.toFile());

        record Case(String article, Audience regex, Audience ai, Audience gold, boolean agreed) {
        }
        List<Case> cases = new ArrayList<>();
        Set<String> depts = new LinkedHashSet<>();
        Set<Regions.Region> homes = new LinkedHashSet<>();
        for (JsonNode n : notices) {
            String article = n.get("articleNo").asText();
            Audience regex = Audience.classify(n.get("category").asText(), n.get("title").asText(), n.get("body").asText(""));
            // 결과 발표는 홈에 카드가 나오지 않는다.
            if (Audience.RESULT.equals(regex.scope())) {
                continue;
            }
            AudienceFacts goldFacts = facts(gold.path("notices").path(article).path("facts"));
            JsonNode h = haiku.path("notices").path(article);
            assertThat(goldFacts).as("정답지에 공지 %s가 없다", article).isNotNull();
            assertThat(h.isMissingNode()).as("Haiku 기록에 공지 %s가 없다", article).isFalse();
            // 운영과 같이 두 번 읽은 조건이 같을 때만 AI 결과를 쓴다. 같은지는 운영 코드로 다시 판단한다.
            AudienceFacts first = facts(h.get("first"));
            AudienceFacts second = facts(h.get("second"));
            boolean agreed = first != null && second != null && first.sameConditions(second);
            cases.add(new Case(article, regex, agreed ? regex.withAi(first) : regex, regex.withAi(goldFacts), agreed));
            depts.addAll(goldFacts.depts());
            goldFacts.regions().stream().map(Regions::parse).flatMap(java.util.Optional::stream).forEach(homes::add);
            homes.addAll(regex.regions());
        }
        depts.add(OTHER_DEPT);

        List<Feed.Viewer> viewers = new ArrayList<>();
        List<Regions.Region> homeChoices = new ArrayList<>(homes);
        homeChoices.add(null);
        for (int grade = 1; grade <= 4; grade++) {
            for (String dept : depts) {
                for (Regions.Region home : homeChoices) {
                    viewers.add(new Feed.Viewer(grade, dept, null, home));
                }
            }
        }

        Score regexScore = new Score();
        Score aiScore = new Score();
        List<ObjectNode> misses = new ArrayList<>();
        for (Case c : cases) {
            boolean regexExact = true;
            boolean aiExact = true;
            int aiWrong = 0;
            for (Feed.Viewer v : viewers) {
                boolean expected = Feed.mineReason(c.gold(), null, v) != null;
                boolean r = Feed.mineReason(c.regex(), null, v) != null;
                boolean a = Feed.mineReason(c.ai(), null, v) != null;
                regexExact &= tally(regexScore, expected, r);
                boolean same = tally(aiScore, expected, a);
                aiExact &= same;
                aiWrong += same ? 0 : 1;
            }
            regexScore.exact += regexExact ? 1 : 0;
            aiScore.exact += aiExact ? 1 : 0;
            if (!aiExact) {
                misses.add(JSON.createObjectNode().put("articleNo", c.article()).put("agreed", c.agreed()).put("wrongPairs", aiWrong));
            }
        }
        int agreed = (int) cases.stream().filter(Case::agreed).count();

        ObjectNode summary = JSON.createObjectNode();
        summary.put("measuredOn", LocalDate.now().toString());
        summary.put("notices", cases.size());
        summary.put("students", viewers.size());
        summary.put("humanChecked", gold.path("humanChecked").asInt(0));
        summary.put("runModel", AudienceLabelRun.HAIKU);
        summary.put("goldModel", gold.path("labeler").asText(AudienceLabelRun.OPUS));
        summary.put("runSource", haiku.path("source").asText(""));
        summary.put("agreed", agreed);
        summary.set("regex", score(regexScore));
        summary.set("ai", score(aiScore));
        writeReport(summary, misses);
        checkPublishedSummary(summary);

        JsonNode baseline = JSON.readTree(Fixtures.read("gold/audience-baseline.json"));
        assertThat(aiScore.precision()).as("AI 형광펜 정밀도").isGreaterThanOrEqualTo(baseline.get("precision").asDouble());
        assertThat(aiScore.recall()).as("AI 형광펜 재현율").isGreaterThanOrEqualTo(baseline.get("recall").asDouble());
    }

    private static boolean tally(Score s, boolean expected, boolean predicted) {
        s.expected += expected ? 1 : 0;
        s.predicted += predicted ? 1 : 0;
        s.hit += expected && predicted ? 1 : 0;
        return expected == predicted;
    }

    private static AudienceFacts facts(JsonNode node) {
        return node == null || node.isMissingNode() ? null : AudienceFacts.fromJson(node.toString());
    }

    private static ObjectNode score(Score s) {
        return JSON.createObjectNode()
                .put("precision", Math.floor(s.precision() * 1000) / 1000)
                .put("recall", Math.floor(s.recall() * 1000) / 1000)
                .put("expected", s.expected).put("predicted", s.predicted).put("hit", s.hit).put("exactNotices", s.exact);
    }

    private static void writeReport(ObjectNode summary, List<ObjectNode> misses) throws Exception {
        ObjectNode root = summary.deepCopy();
        root.set("aiMisses", JSON.valueToTree(misses));
        Path out = Path.of("build/reports/audience/report.json");
        Files.createDirectories(out.getParent());
        JSON.writeValue(out.toFile(), root);
    }

    private static void checkPublishedSummary(ObjectNode now) throws Exception {
        Path published = Path.of("src/main/resources/quality/audience.json");
        if (System.getenv("QUALITY_UPDATE") != null || !Files.exists(published)) {
            Files.createDirectories(published.getParent());
            JSON.writeValue(published.toFile(), now);
            return;
        }
        ObjectNode before = (ObjectNode) JSON.readTree(published.toFile());
        ObjectNode current = now.deepCopy();
        before.remove("measuredOn");
        current.remove("measuredOn");
        assertThat(Objects.equals(before, current)).as("공개 형광펜 요약이 이번 결과와 다르다. QUALITY_UPDATE=1로 다시 쓴다").isTrue();
    }
}
