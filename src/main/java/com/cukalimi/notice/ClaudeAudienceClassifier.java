package com.cukalimi.notice;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonSchemaLocalValidation;
import com.anthropic.models.messages.MessageCountTokensParams;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import com.cukalimi.config.AppProperties;
import com.cukalimi.feed.AudienceFacts;
import com.cukalimi.feed.Regions;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Claude가 공지 글에서 대상 조건을 정해진 JSON 형식으로 옮겨 적는다 (ADR 013). 운영은 Haiku로 두 번 읽는다.
 * 정답지를 만들 때는 같은 문구로 Opus를 부른다(AudienceLabelRun). 문구를 바꾸면 정답지와 채점을 다시 돌린다.
 */
@Component
public class ClaudeAudienceClassifier implements AudienceClassifier {

    static final String PROMPT = """
            너는 가톨릭대학교 공지에서 지원 대상 조건만 옮겨 적는다. 공지에 글자로 적힌 조건만 적고, 추측하거나 보태지 마라.

            grades
            - "2~4학년"은 [2,3,4], "2학년 이상"은 [2,3,4], "3학년"은 [3]이다.
            - 졸업예정자·졸업대상자는 [4], 신입생은 [1]이다. "졸업예정자는 지원 불가"처럼 빼라고 적혀 있으면 4를 뺀다.
            - "1학년 이상 수료"는 2학년부터다. 학기 수로 적혀 있으면 지금 몇 학기째인지로 바꾼다. "4학기 이상 이수"는 5학기째부터다.
              1~2학기째 1학년, 3~4학기째 2학년, 5~6학기째 3학년, 7~8학기째 4학년이다.
            - "재학생"만 있거나 학년 말이 없거나 전 학년이 해당하면 빈 목록이다.

            depts
            - 특정 학과·학부·전공·단과대학만 지원할 수 있을 때 그 이름을 공지에 적힌 그대로 적는다. 우대만 하면 적지 않는다.

            regions
            - 본인이나 부모의 주소, 주민등록, 출신 학교, 연고 지역 조건일 때만 적는다. 학교 주소, 접수처 주소, 행사 장소는 조건이 아니다.
            - 시·군·구나 시·도가 세운 장학재단·장학회(춘천인재육성장학재단, 논산시장학회, 전남인재평생교육진흥원 등)의 장학은 본문에 따로 없어도 그 지역 연고 조건으로 적는다.
              기업이나 사람 이름의 재단(미래에셋박현주재단, 두산연강재단 등)은 지역 조건이 아니다.
            - 시·도는 다음 이름 중 하나로 시작한다. %s
            - 시·군·구까지 적혀 있으면 "전라남도 순천시"처럼 시·도 이름 뒤에 띄어 쓴다.

            blockers
            - 지원자를 실제로 좁히는 조건만 넣는다. 재학 중, 징계 없음, 마지막 학기 아님, 중복 수혜 금지, 편입생·복학생은 따로 계산한다는 예외 문장처럼
              대부분의 학생이 해당하거나 일부에게만 붙는 단서는 넣지 않는다.
            - INCOME 소득 분위, 기초생활수급, 차상위 같은 경제 조건
            - GPA 직전 학기 성적, 평점, 이수 학점 조건
            - GROUP 장애, 다문화, 보훈, 북한이탈, 농어촌, 외국인 유학생, 교환학생 파견 예정자처럼 그 계층·신분만 지원할 수 있는 공지
            - COURSE 특정 과목을 수강하는 학생만 해당
            - IMAGE_ONLY 본문에 대상 조건이 하나도 없고 그림이나 첨부 파일을 보라고만 적혀 있음
            - OTHER 그 밖에 학년·학과·지역으로 가릴 수 없는 조건. 학자금 대출 이용자, 출생 연도, 수상 경력, 특정 트랙 이수자, 이미 신청한 학생만 보는 안내가 그렇다.
              학생 대상이 아니거나 결과 발표인 공지도 OTHER다.
            - 대상이 서로 다른 여러 프로그램을 한 공지에 묶었으면 모두에게 공통인 조건만 적고 OTHER를 넣는다.

            evidence
            - 조건을 읽은 원문 문장 하나를 그대로 옮긴다. 조건이 없으면 빈 문자열이다.
            """.formatted(Regions.all().stream().map(Regions.Sido::name).collect(Collectors.joining(", ")));

    /** 공지 본문은 대개 수천 자다. 이보다 길면 앞부분만 보낸다. 자격 문단은 앞쪽에 있다. */
    static final int MAX_CHARS = 20_000;

    private final String apiKey;
    private final String model;
    private final OutputConfig.Effort effort;
    private AnthropicClient client;

    @Autowired
    public ClaudeAudienceClassifier(AppProperties props) {
        this(props.transcribe() == null ? null : props.transcribe().apiKey(),
                props.audienceRead() == null || !props.audienceRead().enabled() ? null : props.audienceRead().model(), null);
    }

    /** 정답지 만들기처럼 다른 모델로 부를 때. effort가 null이면 모델 기본값이다. */
    public ClaudeAudienceClassifier(String apiKey, String model, OutputConfig.Effort effort) {
        this.apiKey = apiKey;
        this.model = model;
        this.effort = effort;
    }

    /** 보내기 전에 입력 토큰을 센다. 요금이 없다. 형식 지시(스키마)는 세지 않아 실제보다 조금 적다. */
    public long countInputTokens(String title, String text) {
        return client().messages().countTokens(MessageCountTokensParams.builder()
                .model(model)
                .system(PROMPT)
                .addUserMessage(userText(title, text))
                .build()).inputTokens();
    }

    @Override
    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank();
    }

    @Override
    public Result classify(String title, String text) {
        StructuredMessage<AudienceFacts> message;
        try {
            message = client().messages().create(params(title, text));
        } catch (com.anthropic.errors.BadRequestException | com.anthropic.errors.UnprocessableEntityException e) {
            throw new Failed(e.getMessage(), 0, 0, e);
        }
        long in = message.usage().inputTokens();
        long out = message.usage().outputTokens();
        StopReason stop = message.stopReason().orElse(null);
        if (!StopReason.END_TURN.equals(stop)) {
            throw new Failed("대상 읽기가 끝까지 가지 않았습니다: " + stop, in, out, null);
        }
        AudienceFacts facts;
        try {
            facts = message.content().stream().flatMap(b -> b.text().stream()).map(t -> t.text()).findFirst()
                    .orElseThrow(() -> new IllegalStateException("응답에 글이 없습니다"));
        } catch (RuntimeException e) {
            throw new Failed("대상 읽기 응답을 읽지 못했습니다: " + e.getMessage(), in, out, e);
        }
        return new Result(facts.normalized(), model, in, out);
    }

    private static String userText(String title, String text) {
        String body = text == null ? "" : text;
        if (body.length() > MAX_CHARS) {
            body = body.substring(0, MAX_CHARS);
        }
        return "제목: " + title + "\n\n본문:\n" + body;
    }

    StructuredMessageCreateParams<AudienceFacts> params(String title, String text) {
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(model)
                .maxTokens(2000L)
                .system(PROMPT);
        // Haiku는 옮겨 적기라 생각을 끄면 출력 토큰이 준다. Opus 5.5는 생각을 끌 수 없어 그대로 둔다.
        if (!model.startsWith("claude-opus")) {
            b.thinking(ThinkingConfigDisabled.builder().build());
        }
        StructuredOutputConfig.Builder<AudienceFacts> config = StructuredOutputConfig.<AudienceFacts>builder()
                .format(AudienceFacts.class, JsonSchemaLocalValidation.YES);
        if (effort != null) {
            config.effort(effort);
        }
        return b.outputConfig(config.build())
                .addUserMessage(userText(title, text))
                .build();
    }

    private synchronized AnthropicClient client() {
        if (client == null) {
            client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        }
        return client;
    }
}
