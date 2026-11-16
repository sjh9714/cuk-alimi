package com.cukalimi.quality;

import com.cukalimi.notice.ClaudeDeadlineLocator;
import com.cukalimi.notice.DeadlineLocator;
import com.cukalimi.notice.LocatorMerge;
import com.cukalimi.notice.LocatorReading;
import com.cukalimi.notice.NoticeText;
import com.cukalimi.notice.QuoteCheck;
import com.cukalimi.ops.AiPrices;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 문장 찾기 모델의 답을 녹음한다 (ADR 016). API 요금이 나가므로 평소 테스트에서는 돌지 않는다.
 * 모델의 원본 답만 저장하고 판정은 하지 않는다. 채점(LocatorBacktestTest)이 이 파일을 다시 돌려 API 없이 수치를 낸다.
 *
 * <ul>
 * <li>LOCATOR_RUN=count 입력 토큰을 세고 예상 비용을 적는다(무료).</li>
 * <li>LOCATOR_RUN=haiku 작은 모델이 공지마다 두 번 읽는다.</li>
 * <li>LOCATOR_RUN=opus 작은 모델의 두 읽기가 엇갈린 공지만 큰 모델이 한 번 읽는다. 운영과 같은 조건이다. 먼저 haiku 녹음이 있어야 한다.</li>
 * <li>LOCATOR_SET=dev(기본) 또는 dev2. LOCATOR_RUN_MAX_USD(기본 3)를 넘기 전에 멈춘다.</li>
 * </ul>
 * 키는 ANTHROPIC_API_KEY. 이미 녹음한 공지는 다시 부르지 않아 멈춘 데서 이어 돌릴 수 있다. 지시문이 바뀌었으면 처음부터 다시 녹음한다.
 *
 * <pre>LOCATOR_RUN=haiku ANTHROPIC_API_KEY=... ./gradlew test --tests '*LocatorLabelRun'</pre>
 */
@EnabledIfEnvironmentVariable(named = "LOCATOR_RUN", matches = "count|haiku|opus")
class LocatorLabelRun {

    static final String HAIKU = "claude-haiku-5-5";
    static final String OPUS = "claude-opus-5-5";
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    void run() throws Exception {
        String mode = System.getenv("LOCATOR_RUN");
        String key = System.getenv("ANTHROPIC_API_KEY");
        String set = System.getenv().getOrDefault("LOCATOR_SET", "dev");
        double max = Double.parseDouble(System.getenv().getOrDefault("LOCATOR_RUN_MAX_USD", "3"));
        List<LocatorSets.Case> cases = LocatorSets.load(set);
        switch (mode) {
            case "count" -> count(key, cases);
            case "haiku" -> record(new ClaudeDeadlineLocator(key, HAIKU), cases, set, "haiku", 2, max, null);
            case "opus" -> {
                Path haiku = LocatorSets.recording(set, "haiku");
                JsonNode first = JSON.readTree(haiku.toFile());
                if (!ClaudeDeadlineLocator.promptHash().equals(first.path("promptHash").asText())) {
                    throw new IllegalStateException("작은 모델 녹음의 지시문이 지금과 다르다. haiku부터 다시 녹음한다: " + haiku);
                }
                record(new ClaudeDeadlineLocator(key, OPUS), cases, set, "opus", 1, max, first.get("notices"));
            }
            default -> throw new IllegalArgumentException(mode);
        }
    }

    private void count(String key, List<LocatorSets.Case> cases) {
        ClaudeDeadlineLocator haiku = new ClaudeDeadlineLocator(key, HAIKU);
        long in = 0;
        for (LocatorSets.Case c : cases) {
            in += haiku.countInputTokens(NoticeText.of(c.view()), c.view().postedOn());
        }
        // 답은 공지마다 300토큰쯤으로 잡는다. 큰 모델은 생각한 글까지 2,000토큰으로 잡고 열 건에 한 건 부른다고 본다.
        double haikuUsd = AiPrices.usd(HAIKU, in * 2, cases.size() * 2L * 300);
        double opusUsd = AiPrices.usd(OPUS, in / 10, cases.size() / 10 * 2_000L);
        System.out.printf("LOCATOR_COUNT notices=%d haikuInputOnce=%d haikuTwoReadsUsd=%.3f opusTenthUsd=%.3f%n", cases.size(), in, haikuUsd,
                opusUsd);
    }

    /** @param haikuNotices opus 녹음일 때 작은 모델의 녹음. 두 읽기가 엇갈린 공지만 부른다 */
    private void record(ClaudeDeadlineLocator locator, List<LocatorSets.Case> cases, String set, String who, int reads, double maxUsd,
            JsonNode haikuNotices) throws Exception {
        Path out = LocatorSets.recording(set, who);
        String hash = ClaudeDeadlineLocator.promptHash();
        ObjectNode root = Files.exists(out) ? (ObjectNode) JSON.readTree(out.toFile()) : JSON.createObjectNode();
        if (!hash.equals(root.path("promptHash").asText())) {
            root = JSON.createObjectNode();
        }
        root.put("about", "문장 찾기 녹음 (ADR 016). " + locator.model() + "의 원본 답이다. 사람이 고치지 않는다. "
                + "채점(LocatorBacktestTest)이 이 답을 원문에 맞춰 보고 날짜를 읽어 수치를 낸다.");
        root.put("set", set);
        root.put("model", locator.model());
        root.put("promptHash", hash);
        root.put("reads", reads);
        ObjectNode notices = root.has("notices") ? (ObjectNode) root.get("notices") : root.putObject("notices");
        long in = root.path("usage").path("inputTokens").asLong();
        long outTokens = root.path("usage").path("outputTokens").asLong();
        double spent = 0;
        int called = 0;
        for (LocatorSets.Case c : cases) {
            if (notices.has(c.articleNo())) {
                continue;
            }
            NoticeText text = NoticeText.of(c.view());
            if (haikuNotices != null && !disagreed(c, text, haikuNotices.get(c.articleNo()))) {
                continue;
            }
            if (spent >= maxUsd) {
                System.out.printf("LOCATOR_LABEL 상한 %.2f달러에 닿아 멈춘다. 남은 공지는 다시 돌리면 이어진다.%n", maxUsd);
                break;
            }
            ObjectNode entry = JSON.createObjectNode();
            entry.put("title", c.view().title());
            ArrayNode list = entry.putArray("reads");
            for (int r = 0; r < reads; r++) {
                ObjectNode read = list.addObject();
                long i;
                long o;
                try {
                    DeadlineLocator.Result result = locator.locate(text, c.view().postedOn());
                    i = result.inputTokens();
                    o = result.outputTokens();
                    read.set("found", JSON.valueToTree(result.reading().found()));
                } catch (DeadlineLocator.Failed f) {
                    i = f.inputTokens();
                    o = f.outputTokens();
                    read.putArray("found");
                    read.put("failed", String.valueOf(f.getMessage()));
                }
                in += i;
                outTokens += o;
                spent += AiPrices.usd(locator.model(), i, o);
            }
            called++;
            notices.set(c.articleNo(), entry);
            ObjectNode usage = root.putObject("usage");
            usage.put("inputTokens", in);
            usage.put("outputTokens", outTokens);
            usage.put("usd", Math.round(AiPrices.usd(locator.model(), in, outTokens) * 10_000) / 10_000.0);
            JSON.writeValue(out.toFile(), root);
        }
        JSON.writeValue(out.toFile(), root);
        System.out.printf("LOCATOR_LABEL set=%s model=%s called=%d recorded=%d thisRunUsd=%.4f totalIn=%d totalOut=%d%n", set, locator.model(),
                called, notices.size(), spent, in, outTokens);
    }

    /** 작은 모델의 두 읽기가 결과를 바꿀 만큼 엇갈렸는가. 운영이 큰 모델을 부르는 조건과 같다. */
    private static boolean disagreed(LocatorSets.Case c, NoticeText text, JsonNode haiku) throws Exception {
        if (haiku == null || haiku.path("reads").size() < 2) {
            return false;
        }
        List<QuoteCheck.Located> a = QuoteCheck.check(text, c.view().postedOn(), JSON.treeToValue(haiku.get("reads").get(0), LocatorReading.class));
        List<QuoteCheck.Located> b = QuoteCheck.check(text, c.view().postedOn(), JSON.treeToValue(haiku.get("reads").get(1), LocatorReading.class));
        return LocatorMerge.disagree(c.rule(), a, b);
    }
}
