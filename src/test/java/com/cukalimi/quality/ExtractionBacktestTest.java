package com.cukalimi.quality;

import static org.assertj.core.api.Assertions.assertThat;

import com.cukalimi.Fixtures;
import com.cukalimi.feed.ItemKind;
import com.cukalimi.notice.DateItemExtractor;
import com.cukalimi.notice.ExtractedItem;
import com.cukalimi.notice.ImageNoticeReader;
import com.cukalimi.notice.NoticeView;
import com.cukalimi.notice.NoticeViewParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * 마감 추출 백테스트 (ADR 011). 정답셋의 공지마다 앱과 같은 코드로 학생이 할 일의 마감(ACTION 종료일)을 뽑아 정답과 비교한다.
 * 글 공지는 저장해 둔 본문을, 그림 공지는 받아 적은 글을 넣는다. 그림은 다시 받아 적지 않으므로 API 비용이 없다.
 * 정밀도나 재현율이 baseline.json보다 떨어지면 실패한다. 규칙을 고쳐 수치가 오르면 baseline을 올린다(소수 셋째 자리에서 내림).
 * 어긋난 날짜는 모두 labels.json의 known에 실패 유형으로 적혀 있어야 한다. 분류되지 않은 새 오류가 생기거나,
 * 고친 오류가 known에 남아 있으면 실패한다. 오류 목록과 실제 동작이 늘 같게 유지된다.
 * 결과는 build/reports/extraction/report.json에 남는다.
 * 공개 품질 페이지가 보여 주는 요약(src/main/resources/quality/extraction.json)이 이번 결과와 다르면 실패한다.
 * 수치가 바뀌었으면 QUALITY_UPDATE=1 ./gradlew test --tests '*ExtractionBacktestTest'로 요약을 다시 쓴다.
 */
class ExtractionBacktestTest {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    record Known(String kind, LocalDate date, String type) {
    }

    record Outcome(long articleNo, String source, String category, Set<LocalDate> want, Set<LocalDate> got,
            List<Known> known) {
        /** 실제로 어긋난 날짜. kind는 missed(놓침) 또는 invented(없는 마감을 만듦). */
        Set<String> mismatchKeys() {
            Set<String> keys = new TreeSet<>();
            missed().forEach(d -> keys.add("missed " + d));
            invented().forEach(d -> keys.add("invented " + d));
            return keys;
        }

        Set<String> knownKeys() {
            return known.stream().map(k -> k.kind() + " " + k.date()).collect(Collectors.toCollection(TreeSet::new));
        }

        Set<LocalDate> missed() {
            return want.stream().filter(d -> !got.contains(d)).collect(Collectors.toCollection(TreeSet::new));
        }

        Set<LocalDate> invented() {
            return got.stream().filter(d -> !want.contains(d)).collect(Collectors.toCollection(TreeSet::new));
        }
    }

    record Score(int notices, int exact, int truePositive, int falsePositive, int falseNegative) {
        double precision() {
            return truePositive + falsePositive == 0 ? 1.0 : (double) truePositive / (truePositive + falsePositive);
        }

        double recall() {
            return truePositive + falseNegative == 0 ? 1.0 : (double) truePositive / (truePositive + falseNegative);
        }

        static Score of(List<Outcome> outcomes) {
            int tp = 0;
            int fp = 0;
            int fn = 0;
            int exact = 0;
            for (Outcome o : outcomes) {
                int missed = o.missed().size();
                int invented = o.invented().size();
                tp += o.want().size() - missed;
                fp += invented;
                fn += missed;
                exact += missed == 0 && invented == 0 ? 1 : 0;
            }
            return new Score(outcomes.size(), exact, tp, fp, fn);
        }
    }

    @Test
    void deadlineExtractionDoesNotRegress() throws Exception {
        JsonNode gold = JSON.readTree(Fixtures.read("gold/labels.json"));
        JsonNode labels = gold.get("notices");
        List<Outcome> outcomes = new java.util.ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = labels.fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> e = it.next();
            long no = Long.parseLong(e.getKey());
            String source = e.getValue().get("source").asText();
            NoticeView view = NoticeViewParser.parse(Fixtures.read("gold/notices/" + no + ".html"));
            List<ExtractedItem> items = "image".equals(source)
                    ? ImageNoticeReader.extractFromTranscripts(view, List.of(Fixtures.read("gold/transcripts/" + no + ".html")))
                    : DateItemExtractor.extract(view);
            Set<LocalDate> got = items.stream().filter(i -> ItemKind.ACTION.equals(ItemKind.of(i.title())))
                    .map(i -> i.range().end()).collect(Collectors.toCollection(TreeSet::new));
            Set<LocalDate> want = new TreeSet<>();
            e.getValue().get("deadlines").forEach(d -> want.add(LocalDate.parse(d.get("date").asText())));
            List<Known> known = new java.util.ArrayList<>();
            if (e.getValue().has("known")) {
                e.getValue().get("known").forEach(k -> known.add(new Known(k.get("kind").asText(),
                        LocalDate.parse(k.get("date").asText()), k.get("type").asText())));
            }
            outcomes.add(new Outcome(no, source, view.category(), want, got, known));
        }

        Score all = Score.of(outcomes);
        Score text = Score.of(outcomes.stream().filter(o -> "text".equals(o.source())).toList());
        Score image = Score.of(outcomes.stream().filter(o -> "image".equals(o.source())).toList());
        writeReport(all, text, image, outcomes);
        checkPublishedSummary(summary(gold, all, text, image, outcomes));

        List<String> unclassified = new java.util.ArrayList<>();
        List<String> stale = new java.util.ArrayList<>();
        for (Outcome o : outcomes) {
            o.mismatchKeys().stream().filter(k -> !o.knownKeys().contains(k)).forEach(k -> unclassified.add(o.articleNo() + " " + k));
            o.knownKeys().stream().filter(k -> !o.mismatchKeys().contains(k)).forEach(k -> stale.add(o.articleNo() + " " + k));
        }
        assertThat(unclassified).as("분류되지 않은 새 오류. 원문을 보고 고치거나 labels.json의 known에 유형을 적는다").isEmpty();
        assertThat(stale).as("고쳐진 오류가 known에 남아 있다. 지우고 baseline을 올린다").isEmpty();

        JsonNode baseline = JSON.readTree(Fixtures.read("gold/baseline.json"));
        assertThat(all.precision()).as("정밀도. 없는 마감을 만들면 떨어진다")
                .isGreaterThanOrEqualTo(baseline.get("precision").asDouble());
        assertThat(all.recall()).as("재현율. 있는 마감을 놓치면 떨어진다")
                .isGreaterThanOrEqualTo(baseline.get("recall").asDouble());
    }

    /** 공개 품질 페이지용 요약. 날짜(measuredOn)는 비교에서 뺀다. */
    private static ObjectNode summary(JsonNode gold, Score all, Score text, Score image, List<Outcome> outcomes) {
        ObjectNode root = JSON.createObjectNode();
        root.put("measuredOn", LocalDate.now().toString());
        root.put("notices", all.notices());
        root.put("deadlines", outcomes.stream().mapToInt(o -> o.want().size()).sum());
        root.set("all", score(all));
        root.set("text", score(text));
        root.set("image", score(image));
        Map<String, int[]> counts = new java.util.TreeMap<>();
        outcomes.forEach(o -> o.known().forEach(k -> counts.computeIfAbsent(k.type(), t -> new int[2])[
                "missed".equals(k.kind()) ? 0 : 1]++));
        ArrayNode remaining = root.putArray("remaining");
        counts.forEach((type, c) -> remaining.addObject().put("type", type)
                .put("description", gold.get("failureTypes").path(type).asText(type)).put("missed", c[0]).put("invented", c[1]));
        return root;
    }

    private static void checkPublishedSummary(ObjectNode now) throws Exception {
        Path published = Path.of("src/main/resources/quality/extraction.json");
        if (System.getenv("QUALITY_UPDATE") != null || !Files.exists(published)) {
            Files.createDirectories(published.getParent());
            JSON.writeValue(published.toFile(), now);
            return;
        }
        ObjectNode before = (ObjectNode) JSON.readTree(published.toFile());
        ObjectNode current = now.deepCopy();
        before.remove("measuredOn");
        current.remove("measuredOn");
        assertThat(before).as("공개 품질 요약이 이번 백테스트 결과와 다르다. QUALITY_UPDATE=1로 다시 쓴다").isEqualTo(current);
    }

    private static void writeReport(Score all, Score text, Score image, List<Outcome> outcomes) throws Exception {
        ObjectNode root = JSON.createObjectNode();
        root.set("all", score(all));
        root.set("text", score(text));
        root.set("image", score(image));
        ArrayNode misses = root.putArray("mismatches");
        for (Outcome o : outcomes) {
            if (o.missed().isEmpty() && o.invented().isEmpty()) {
                continue;
            }
            ObjectNode m = misses.addObject();
            m.put("articleNo", o.articleNo());
            m.put("source", o.source());
            m.put("category", o.category());
            m.set("missed", JSON.valueToTree(o.missed().stream().map(LocalDate::toString).toList()));
            m.set("invented", JSON.valueToTree(o.invented().stream().map(LocalDate::toString).toList()));
        }
        ObjectNode byType = root.putObject("failuresByType");
        for (Outcome o : outcomes) {
            for (Known k : o.known()) {
                ObjectNode t = byType.has(k.type()) ? (ObjectNode) byType.get(k.type()) : byType.putObject(k.type());
                t.put(k.kind(), t.path(k.kind()).asInt() + 1);
            }
        }
        Path out = Path.of("build/reports/extraction/report.json");
        Files.createDirectories(out.getParent());
        JSON.writeValue(out.toFile(), root);
        System.out.printf("extraction backtest: notices=%d exact=%d precision=%.3f recall=%.3f (text %.3f/%.3f, image %.3f/%.3f)%n",
                all.notices(), all.exact(), all.precision(), all.recall(), text.precision(), text.recall(),
                image.precision(), image.recall());
    }

    private static ObjectNode score(Score s) {
        ObjectNode n = JSON.createObjectNode();
        n.put("notices", s.notices());
        n.put("exactNotices", s.exact());
        n.put("truePositive", s.truePositive());
        n.put("falsePositive", s.falsePositive());
        n.put("falseNegative", s.falseNegative());
        n.put("precision", Math.round(s.precision() * 1000) / 1000.0);
        n.put("recall", Math.round(s.recall() * 1000) / 1000.0);
        return n;
    }
}
