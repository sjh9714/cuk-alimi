package com.cukalimi.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.cukalimi.notice.ClaudeDeadlineLocator;
import com.cukalimi.notice.LocatorMerge;
import com.cukalimi.notice.LocatorReading;
import com.cukalimi.notice.NoticeText;
import com.cukalimi.notice.QuoteCheck;
import com.cukalimi.notice.QuoteCheck.Located;
import com.cukalimi.ops.AiPrices;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * 문장 찾기 백테스트 (ADR 016). 녹음해 둔 모델의 답(gold/locator-*.json)을 운영과 같은 코드(QuoteCheck, LocatorMerge)에 넣어
 * 마감 정답과 비교한다. API를 부르지 않는다. 녹음이 없으면 건너뛴다.
 *
 * <p>지시문 해시가 녹음과 다르면 실패한다. 지시문을 고쳤으면 다시 녹음해야 수치가 지금 코드의 것이다.
 * 규칙만, AI만, 규칙+AI(운영), 거부권(AI가 아니라고 한 규칙 마감을 숨김)을 나란히 잰다.
 * 결과는 build/reports/locator/에 남고, 공개 요약(src/main/resources/quality/locator.json)이 이번 결과와 다르면 실패한다.
 * 수치가 바뀌었으면 QUALITY_UPDATE=1로 다시 쓴다.
 */
class LocatorBacktestTest {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final Path PUBLISHED = Path.of("src/main/resources/quality/locator.json");

    /** 한 방식의 점수. 단위는 공지 안의 마감일이다. */
    static final class Tally {
        int tp;
        int fp;
        int fn;
        int exact;

        void add(Set<LocalDate> want, Set<LocalDate> got) {
            int hit = (int) want.stream().filter(got::contains).count();
            tp += hit;
            fn += want.size() - hit;
            fp += got.size() - hit;
            exact += got.equals(want) ? 1 : 0;
        }

        ObjectNode json() {
            ObjectNode n = JSON.createObjectNode();
            n.put("found", tp).put("missed", fn).put("wrong", fp).put("exactNotices", exact);
            n.put("precision", ratio(tp, tp + fp)).put("recall", ratio(tp, tp + fn));
            return n;
        }
    }

    @Test
    void recordedLocatorAnswersAreScoredAgainstTheGoldDeadlines() throws Exception {
        ObjectNode now = JSON.createObjectNode();
        for (String set : LocatorSets.NAMES) {
            if (Files.exists(LocatorSets.recording(set, "haiku"))) {
                now.set(set, score(set));
            }
        }
        assumeTrue(!now.isEmpty(), "문장 찾기 녹음이 아직 없다 (LocatorLabelRun)");
        now.put("promptHash", ClaudeDeadlineLocator.promptHash());
        System.out.println("locator: " + now);
        if (System.getenv("QUALITY_UPDATE") != null || !Files.exists(PUBLISHED)) {
            JSON.writeValue(PUBLISHED.toFile(), now);
        }
        assertThat(JSON.readTree(PUBLISHED.toFile())).as("공개 문장 찾기 요약이 이번 결과와 다르다. QUALITY_UPDATE=1로 다시 쓴다").isEqualTo(now);
    }

    private ObjectNode score(String set) throws Exception {
        JsonNode haiku = JSON.readTree(LocatorSets.recording(set, "haiku").toFile());
        Path opusFile = LocatorSets.recording(set, "opus");
        JsonNode opus = Files.exists(opusFile) ? JSON.readTree(opusFile.toFile()) : null;
        String hash = ClaudeDeadlineLocator.promptHash();
        assertThat(haiku.path("promptHash").asText()).as("지시문이 " + set + " 녹음과 다르다. LocatorLabelRun으로 다시 녹음한다").isEqualTo(hash);
        if (opus != null) {
            assertThat(opus.path("promptHash").asText()).as("지시문이 " + set + " 큰 모델 녹음과 다르다. 다시 녹음한다").isEqualTo(hash);
        }

        Map<String, Tally> tallies = new LinkedHashMap<>();
        for (String k : List.of("rule", "aiOneRead", "aiTwoVotes", "ruleAndAi", "ruleAndAiVeto")) {
            tallies.put(k, new Tally());
        }
        int notices = 0, deadlines = 0, quotes = 0, unverified = 0, noDate = 0, failed = 0, disagreed = 0, thirdReads = 0;
        int held = 0, heldRight = 0, doubt = 0, doubtRight = 0, added = 0, addedRight = 0;
        List<String> report = new ArrayList<>();
        for (LocatorSets.Case c : LocatorSets.load(set)) {
            JsonNode rec = haiku.path("notices").get(c.articleNo());
            assertThat(rec).as(set + " 녹음에 공지 " + c.articleNo() + "가 없다. LocatorLabelRun을 이어 돌린다").isNotNull();
            NoticeText text = NoticeText.of(c.view());
            List<List<Located>> readings = new ArrayList<>();
            for (JsonNode read : rec.get("reads")) {
                failed += read.has("failed") ? 1 : 0;
                readings.add(QuoteCheck.check(text, c.view().postedOn(), JSON.treeToValue(read, LocatorReading.class)));
            }
            for (List<Located> reading : readings) {
                for (Located l : reading) {
                    quotes++;
                    unverified += l.status() == QuoteCheck.Status.UNVERIFIED ? 1 : 0;
                    noDate += l.status() == QuoteCheck.Status.NO_DATE ? 1 : 0;
                    if (!l.read()) {
                        report.add(c.articleNo() + " " + l.status() + " \"" + l.found().quote() + "\"");
                    }
                }
            }
            Set<LocalDate> rule = LocatorMerge.ruleDeadlines(c.rule());
            Set<LocalDate> one = LocatorMerge.dates(readings.get(0), true);
            Set<LocalDate> two = new TreeSet<>(one);
            two.retainAll(LocatorMerge.dates(readings.get(1), true));
            // 운영과 같다. 두 읽기가 엇갈렸고 큰 모델의 녹음이 있으면 세 번째 표로 넣는다.
            if (LocatorMerge.disagree(c.rule(), readings.get(0), readings.get(1))) {
                disagreed++;
                JsonNode third = opus == null ? null : opus.path("notices").get(c.articleNo());
                if (third != null) {
                    thirdReads++;
                    readings.add(QuoteCheck.check(text, c.view().postedOn(), JSON.treeToValue(third.get("reads").get(0), LocatorReading.class)));
                }
            }
            LocatorMerge.Result merged = LocatorMerge.merge(c.view(), c.rule(), readings);
            Set<LocalDate> addedDates = new TreeSet<>();
            merged.added().forEach(i -> addedDates.add(i.range().end()));
            Set<LocalDate> heldDates = new TreeSet<>();
            merged.held().forEach(i -> heldDates.add(i.range().end()));
            Set<LocalDate> both = new TreeSet<>(rule);
            both.addAll(addedDates);
            Set<LocalDate> veto = new TreeSet<>(both);
            veto.removeAll(merged.doubt());

            tallies.get("rule").add(c.want(), rule);
            tallies.get("aiOneRead").add(c.want(), one);
            tallies.get("aiTwoVotes").add(c.want(), two);
            tallies.get("ruleAndAi").add(c.want(), both);
            tallies.get("ruleAndAiVeto").add(c.want(), veto);
            notices++;
            deadlines += c.want().size();
            added += addedDates.size();
            addedRight += (int) addedDates.stream().filter(c.want()::contains).count();
            held += heldDates.size();
            heldRight += (int) heldDates.stream().filter(c.want()::contains).count();
            doubt += merged.doubt().size();
            // 의심이 옳았다는 것은 규칙이 올린 그 날짜가 정답에 없다는 뜻이다.
            doubtRight += (int) merged.doubt().stream().filter(d -> !c.want().contains(d)).count();
            if (!both.equals(c.want()) || !one.equals(c.want())) {
                report.add(c.articleNo() + " 정답 " + c.want() + " 규칙 " + rule + " AI첫읽기 " + one + " 더함 " + addedDates + " 보류 " + heldDates
                        + " 의심 " + merged.doubt() + " | " + c.view().title());
            }
        }
        Files.createDirectories(Path.of("build/reports/locator"));
        Files.writeString(Path.of("build/reports/locator/" + set + ".txt"), String.join("\n", report) + "\n");

        ObjectNode s = JSON.createObjectNode();
        s.put("notices", notices).put("deadlines", deadlines);
        tallies.forEach((k, t) -> s.set(k, t.json()));
        s.putObject("quotes").put("total", quotes).put("notInNotice", unverified).put("noDateRead", noDate).put("failedReads", failed);
        s.putObject("added").put("total", added).put("right", addedRight);
        s.putObject("held").put("total", held).put("right", heldRight);
        s.putObject("doubt").put("total", doubt).put("right", doubtRight);
        s.put("disagreedNotices", disagreed).put("thirdReads", thirdReads);
        ObjectNode cost = s.putObject("cost");
        double usd = usd(haiku) + (opus == null ? 0 : usd(opus));
        cost.put("smallModel", haiku.path("model").asText()).put("smallModelUsd", round4(usd(haiku)));
        if (opus != null) {
            cost.put("largeModel", opus.path("model").asText()).put("largeModelUsd", round4(usd(opus)));
        }
        cost.put("usdPerNotice", round4(notices == 0 ? 0 : usd / notices));
        return s;
    }

    private static double usd(JsonNode recording) {
        return AiPrices.usd(recording.path("model").asText(), recording.path("usage").path("inputTokens").asLong(),
                recording.path("usage").path("outputTokens").asLong());
    }

    private static double ratio(int a, int b) {
        return b == 0 ? 1.0 : Math.round(a * 1000.0 / b) / 1000.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10_000) / 10_000.0;
    }
}
