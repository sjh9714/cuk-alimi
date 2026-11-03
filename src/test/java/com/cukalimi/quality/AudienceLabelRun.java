package com.cukalimi.quality;

import com.anthropic.models.messages.OutputConfig;
import com.cukalimi.feed.AudienceFacts;
import com.cukalimi.notice.AudienceClassifier;
import com.cukalimi.notice.ClaudeAudienceClassifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 대상 조건 정답지와 운영 모델 측정을 만든다 (ADR 013). API 요금이 나가므로 평소 테스트에서는 돌지 않는다.
 * AUDIENCE_SET=holdout이면 시험용 셋으로 돈다. AUDIENCE_RUN=count 입력 토큰만 센다(무료). opus 정답지 gold/audience.json을 만든다. haiku 운영 모델로 두 번 읽어 gold/audience-haiku.json을 만든다.
 * 키는 ANTHROPIC_API_KEY. 결과 파일에 이미 있는 공지는 다시 부르지 않으니 중간에 멈춰도 이어서 돌릴 수 있다.
 *
 * <pre>AUDIENCE_RUN=count ANTHROPIC_API_KEY=... ./gradlew test --tests '*AudienceLabelRun'</pre>
 */
@EnabledIfEnvironmentVariable(named = "AUDIENCE_RUN", matches = "count|opus|haiku")
class AudienceLabelRun {

    static final String OPUS = "claude-opus-5-5";
    static final String HAIKU = "claude-haiku-5-5";
    static final Path GOLD = Path.of("src/test/resources/fixtures/gold/audience.json");
    static final Path HAIKU_OUT = Path.of("src/test/resources/fixtures/gold/audience-haiku.json");
    /** 지시문을 고칠 때 보지 않는 시험용 셋 (2026-11-03에 학교 게시판 뒤쪽 페이지에서 받은 70건). */
    static final String HOLDOUT_NOTICES = "/fixtures/notices-holdout-2026-11-03.json";
    static final Path HOLDOUT_GOLD = Path.of("src/test/resources/fixtures/gold/audience-holdout.json");
    static final Path HOLDOUT_HAIKU = Path.of("src/test/resources/fixtures/gold/audience-holdout-haiku.json");
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    void run() throws Exception {
        String mode = System.getenv("AUDIENCE_RUN");
        String key = System.getenv("ANTHROPIC_API_KEY");
        boolean holdout = "holdout".equals(System.getenv("AUDIENCE_SET"));
        JsonNode notices = JSON.readTree(AudienceLabelRun.class.getResourceAsStream(
                holdout ? HOLDOUT_NOTICES : "/fixtures/notices-2026-10-26.json"));
        switch (mode) {
            case "count" -> count(key, notices);
            case "opus" -> label(new ClaudeAudienceClassifier(key, OPUS, OutputConfig.Effort.MEDIUM), notices, GOLD, 1,
                    "대상 조건 정답지 (ADR 013). " + OPUS + "가 운영과 같은 지시문으로 공지 62건(notices-2026-10-26.json)을 한 번 읽었다. "
                            + "사람이 쓴 정답이 아니라 더 강한 모델의 답이다. 사람 표본 확인은 humanChecked에 적는다.");
            case "haiku" -> label(new ClaudeAudienceClassifier(key, HAIKU, null), notices, holdout ? HOLDOUT_HAIKU : HAIKU_OUT, 2,
                    "운영 모델(" + HAIKU + ")이 같은 공지를 두 번 읽은 기록 (ADR 013). 채점 테스트가 API를 부르지 않도록 응답을 저장해 둔다.");
            default -> throw new IllegalArgumentException(mode);
        }
    }

    private void count(String key, JsonNode notices) {
        ClaudeAudienceClassifier haiku = new ClaudeAudienceClassifier(key, HAIKU, null);
        ClaudeAudienceClassifier opus = new ClaudeAudienceClassifier(key, OPUS, null);
        long h = 0;
        long o = 0;
        for (JsonNode n : notices) {
            h += haiku.countInputTokens(n.get("title").asText(), n.get("body").asText(""));
            o += opus.countInputTokens(n.get("title").asText(), n.get("body").asText(""));
        }
        System.out.printf("AUDIENCE_COUNT notices=%d haikuInput=%d opusInput=%d%n", notices.size(), h, o);
    }

    private void label(AudienceClassifier classifier, JsonNode notices, Path out, int reads, String about) throws Exception {
        ObjectNode root = Files.exists(out) ? (ObjectNode) JSON.readTree(out.toFile()) : JSON.createObjectNode();
        // 세션에서 만든 근사 기록이나 정답지는 API 결과로 이어 쓰지 않고 새로 만든다.
        if (!root.path("about").asText("").equals(about)) {
            root = JSON.createObjectNode();
        }
        root.put("about", about);
        if (reads > 1) {
            root.put("source", "운영과 같은 API 호출(구조화 출력)로 두 번씩 읽혔어요");
        } else {
            root.put("labeler", OPUS + " (API)");
        }
        ObjectNode byArticle = root.has("notices") ? (ObjectNode) root.get("notices") : root.putObject("notices");
        long in = root.path("usage").path("inputTokens").asLong();
        long outTokens = root.path("usage").path("outputTokens").asLong();
        for (JsonNode n : notices) {
            String article = n.get("articleNo").asText();
            if (byArticle.has(article)) {
                continue;
            }
            ObjectNode entry = JSON.createObjectNode();
            entry.put("category", n.get("category").asText());
            entry.put("title", n.get("title").asText());
            AudienceFacts first = null;
            boolean same = true;
            for (int r = 0; r < reads; r++) {
                AudienceClassifier.Result result = classifier.classify(n.get("title").asText(), n.get("body").asText(""));
                in += result.inputTokens();
                outTokens += result.outputTokens();
                entry.set(reads == 1 ? "facts" : r == 0 ? "first" : "second", JSON.readTree(result.facts().toJson()));
                if (result.reading() != null) {
                    entry.set(reads == 1 ? "reading" : r == 0 ? "firstReading" : "secondReading", JSON.valueToTree(result.reading()));
                }
                if (first == null) {
                    first = result.facts();
                } else {
                    same = first.sameConditions(result.facts());
                }
            }
            if (reads > 1) {
                entry.put("agreed", same);
            }
            byArticle.set(article, entry);
            ObjectNode usage = root.putObject("usage");
            usage.put("inputTokens", in);
            usage.put("outputTokens", outTokens);
            JSON.writeValue(out.toFile(), root);
            System.out.printf("AUDIENCE_LABEL %s in=%d out=%d%n", article, in, outTokens);
        }
    }
}
