package com.cukalimi.quality;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonSchemaLocalValidation;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.ThinkingConfigDisabled;
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
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 행 이름을 AI(Haiku)가 지으면 규칙보다 나은지 잰다 (2026-11-04 점검 노트 5번). API 요금이 나가므로 평소 테스트에서는 돌지 않는다.
 * 정답 라벨이 있는 마감마다 공지 제목, 본문, 날짜, 규칙이 뽑은 라벨을 주고 "학생이 플래너에서 볼 한 줄"을 받는다.
 * 결과는 gold/row-names-haiku.json에 남기고, 정답과 글자 그대로 같은 수를 규칙과 나란히 찍는다.
 *
 * <pre>ROW_NAME_RUN=haiku ANTHROPIC_API_KEY=... ./gradlew test --tests '*RowNameRun'</pre>
 */
@EnabledIfEnvironmentVariable(named = "ROW_NAME_RUN", matches = "haiku")
class RowNameRun {

    static final String HAIKU = "claude-haiku-5-5";
    static final Path OUT = Path.of("src/test/resources/fixtures/gold/row-names-haiku.json");
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final int BODY_LIMIT = 4000;

    static final String PROMPT = """
            너는 가톨릭대학교 공지에서 학생이 플래너에 적을 "할 일 한 줄"을 짓는다.
            공지 제목, 본문, 마감 날짜, 그리고 규칙이 그 날짜에 붙인 라벨을 준다.
            - 그 날짜까지 학생이 해야 하는 일을 공지에 적힌 말로만 30자 이내로 적는다. 새 말을 지어내지 않는다.
            - 표의 첫 칸 이름이나 "접수기간" 앞의 일 이름처럼 공지가 이미 쓰는 짧은 이름이 있으면 그대로 쓴다.
            - 설명 문장, 괄호 속 조건, "구분", "내용", "일정" 같은 자리 이름은 넣지 않는다.
            - 할 일이 공지 제목 그 자체(장학금 신청처럼)이면 빈 문자열을 돌려준다.
            """;

    public record RowName(String label) {
    }

    @Test
    void run() throws Exception {
        String key = System.getenv("ANTHROPIC_API_KEY");
        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(key).build();
        JsonNode gold = JSON.readTree(Fixtures.read("gold/labels.json")).get("notices");
        ObjectNode root = Files.exists(OUT) ? (ObjectNode) JSON.readTree(OUT.toFile()) : JSON.createObjectNode();
        root.put("about", "행 이름 AI 실험. " + HAIKU + "가 정답 라벨이 있는 마감마다 할 일 한 줄을 지은 기록. 채점은 ExtractionBacktest와 같은 정답(labels.json의 label)이다.");
        ObjectNode byArticle = root.has("notices") ? (ObjectNode) root.get("notices") : root.putObject("notices");
        long in = root.path("usage").path("inputTokens").asLong();
        long out = root.path("usage").path("outputTokens").asLong();
        int total = 0;
        int aiExact = 0;
        int ruleExact = 0;
        for (Iterator<Map.Entry<String, JsonNode>> it = gold.fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> e = it.next();
            String no = e.getKey();
            boolean image = "image".equals(e.getValue().get("source").asText());
            NoticeView view = NoticeViewParser.parse(Fixtures.read("gold/notices/" + no + ".html"));
            String body = image ? Jsoup.parse(Fixtures.read("gold/transcripts/" + no + ".html")).text() : view.bodyText();
            List<ExtractedItem> items = image
                    ? ImageNoticeReader.extractFromTranscripts(view, List.of(Fixtures.read("gold/transcripts/" + no + ".html")))
                    : DateItemExtractor.extract(view);
            for (JsonNode d : e.getValue().get("deadlines")) {
                if (!d.has("label")) {
                    continue;
                }
                total++;
                LocalDate day = LocalDate.parse(d.get("date").asText());
                String expected = d.get("label").asText();
                List<String> ruleTitles = items.stream()
                        .filter(i -> ItemKind.ACTION.equals(ItemKind.of(i.title())) && i.range().end().equals(day))
                        .map(i -> i.title().equals(view.cleanTitle()) ? "" : i.title()).toList();
                if (ruleTitles.contains(expected)) {
                    ruleExact++;
                }
                String dayKey = day.toString();
                ObjectNode perNotice = byArticle.has(no) ? (ObjectNode) byArticle.get(no) : byArticle.putObject(no);
                if (!perNotice.has(dayKey)) {
                    String ruleLabel = ruleTitles.isEmpty() ? "" : ruleTitles.get(0);
                    StructuredMessage<RowName> message = client.messages().create(MessageCreateParams.builder()
                            .model(HAIKU)
                            .maxTokens(200L)
                            .system(PROMPT)
                            .thinking(ThinkingConfigDisabled.builder().build())
                            .outputConfig(StructuredOutputConfig.<RowName>builder()
                                    .format(RowName.class, JsonSchemaLocalValidation.YES).build())
                            .addUserMessage("제목: " + view.title() + "\n마감 날짜: " + day + "\n규칙이 붙인 라벨: " + ruleLabel
                                    + "\n\n본문:\n" + (body.length() > BODY_LIMIT ? body.substring(0, BODY_LIMIT) : body))
                            .build());
                    in += message.usage().inputTokens();
                    out += message.usage().outputTokens();
                    String ai = message.content().stream().flatMap(b -> b.text().stream()).map(t -> t.text()).findFirst()
                            .map(RowName::label).orElse("").trim();
                    perNotice.putObject(dayKey).put("ai", ai).put("rule", ruleLabel).put("gold", expected);
                    root.putObject("usage").put("inputTokens", in).put("outputTokens", out);
                    JSON.writeValue(OUT.toFile(), root);
                }
                if (perNotice.get(dayKey).get("ai").asText().equals(expected)) {
                    aiExact++;
                }
            }
        }
        double cost = in * 0.10 / 1_000_000 + out * 0.50 / 1_000_000;
        root.put("summary", String.format("deadlines=%d ruleExact=%d aiExact=%d", total, ruleExact, aiExact));
        JSON.writeValue(OUT.toFile(), root);
        System.out.printf("ROW_NAME deadlines=%d ruleExact=%d aiExact=%d input=%d output=%d cost=$%.4f%n", total, ruleExact, aiExact, in,
                out, cost);
    }
}
