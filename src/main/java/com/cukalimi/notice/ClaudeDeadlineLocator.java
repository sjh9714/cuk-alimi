package com.cukalimi.notice;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonSchemaLocalValidation;
import com.anthropic.models.messages.MessageCountTokensParams;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

/**
 * Claude가 공지에서 날짜가 적힌 줄을 글자 그대로 옮긴다 (ADR 016). 답의 형식에 날짜 칸이 없다.
 * 운영은 작은 모델로 두 번 읽고, 둘이 엇갈릴 때만 큰 모델이 같은 지시문으로 한 번 더 읽는다.
 * 지시문을 고치면 녹음(LocatorLabelRun)과 채점(LocatorBacktestTest)을 다시 돌린다. 채점은 지시문 해시가 녹음과 다르면 실패한다.
 */
public class ClaudeDeadlineLocator implements DeadlineLocator {

    static final String PROMPT = """
            너는 가톨릭대학교 공지에서 날짜가 적힌 줄을 찾아 글자 그대로 옮긴다. 날짜를 계산하거나 고쳐 쓰지 않는다. 날짜는 네가 옮긴 줄에서 프로그램이 읽는다.
            공지는 줄 단위 글로 온다. 첫 줄은 제목이고, 표는 한 행이 한 줄이며 칸은 " | "로 이어져 있다.

            찾을 줄
            - 학생이 그날까지 무엇을 해야 하는 마감이 적힌 줄. 신청, 접수, 제출, 납부, 등록, 응시 신청, 설문 응답, 예약, 수령 같은 일이다.
            - "신청기간", "접수기간", "제출기한", "모집기한", "마감", "~까지"처럼 이름이 달라도 학생이 그 안에 해야 하는 일이면 찾는다.
            - 기간으로 적혀 있으면("11. 2.(월) ~ 11. 13.(금)") 시작과 끝이 다 든 줄을 옮긴다.
            - 마감이 여러 개면(1차, 2차, 학교별, 단계별) 줄마다 따로 옮긴다.
            - 마감이 제목에만 있으면 제목 줄을 옮긴다. 본문에도 같은 마감이 있으면 본문의 줄을 옮긴다.
            - 마감처럼 보이지만 학생이 그날까지 할 일이 아닌 날짜가 든 줄도 옮기고 action을 false로 한다.
              결과 발표, 합격자 안내, 시험일, 면접일, 행사·특강·설명회·교육이 열리는 날, 수업·근로·활동·연수 기간, 지급일, 부서나 기관이 하는 심사·추천·선발이 그렇다.

            옮기지 않을 줄
            - 게시일, 문서 번호, 전화번호, 금액, 학점, 인원처럼 날짜가 아닌 숫자만 있는 줄.
            - 출생 연도, 입학 연도, 지난 학기의 성적 기준일처럼 지원 자격을 설명하는 날짜.
            - 이미 지난 차수의 일정이나 지난해 일정.

            quote
            - 공지의 한 줄을 글자 그대로 옮긴다. 낱말을 바꾸거나 줄이거나 보태지 않는다. 숫자, 점, 괄호, 물결표, 요일을 원문 그대로 둔다.
            - 줄이 길면 그 줄 안에서 날짜와 그 앞의 이름이 들어 있는 이어진 부분만 옮긴다. 중간을 건너뛰어 잇지 않는다.
            - 날짜 글자가 반드시 들어 있어야 한다. 이름만 있고 날짜가 다음 줄에 있으면 두 줄을 이어서 옮긴다.

            what
            - 그날까지 하는 일을 2~12자로 적는다("장학금 신청", "서류 제출", "결과 발표"). 날짜나 숫자는 넣지 않는다.

            action
            - 학생이 그날까지 해야 하는 일이면 true, 아니면 false.

            날짜가 든 줄이 없으면 found는 빈 목록이다.
            """;

    private final String apiKey;
    private final String model;
    private AnthropicClient client;

    public ClaudeDeadlineLocator(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    /** 지시문과 답 형식의 설명이 같으면 같은 값이다. 녹음 파일에 적어 두고 채점할 때 맞춰 본다. */
    public static String promptHash() {
        String all = String.join("\n", PROMPT, LocatorReading.FOUND, LocatorReading.QUOTE, LocatorReading.WHAT, LocatorReading.ACTION);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(all.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String model() {
        return model;
    }

    /** 보내기 전에 입력 토큰을 센다. 요금이 없다. 형식 지시(스키마)는 세지 않아 실제보다 조금 적다. */
    public long countInputTokens(NoticeText text, LocalDate postedOn) {
        return client().messages().countTokens(MessageCountTokensParams.builder()
                .model(model)
                .system(PROMPT)
                .addUserMessage(userText(text, postedOn))
                .build()).inputTokens();
    }

    @Override
    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank();
    }

    @Override
    public Result locate(NoticeText text, LocalDate postedOn) {
        StructuredMessage<LocatorReading> message;
        try {
            message = client().messages().create(params(text, postedOn));
        } catch (com.anthropic.errors.BadRequestException | com.anthropic.errors.UnprocessableEntityException e) {
            throw new Failed(e.getMessage(), 0, 0, e);
        }
        long in = message.usage().inputTokens();
        long out = message.usage().outputTokens();
        StopReason stop = message.stopReason().orElse(null);
        if (!StopReason.END_TURN.equals(stop)) {
            throw new Failed("문장 찾기가 끝까지 가지 않았습니다: " + stop, in, out, null);
        }
        LocatorReading reading;
        try {
            reading = message.content().stream().flatMap(b -> b.text().stream()).map(t -> t.text()).findFirst()
                    .orElseThrow(() -> new IllegalStateException("응답에 글이 없습니다"));
        } catch (RuntimeException e) {
            throw new Failed("문장 찾기 응답을 읽지 못했습니다: " + e.getMessage(), in, out, e);
        }
        return new Result(reading, model, in, out);
    }

    static String userText(NoticeText text, LocalDate postedOn) {
        return "게시일: " + postedOn + "\n\n공지:\n" + text.text();
    }

    StructuredMessageCreateParams<LocatorReading> params(NoticeText text, LocalDate postedOn) {
        boolean opus = model.startsWith("claude-opus");
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(model)
                // 큰 모델은 생각한 글도 출력으로 센다. 일정표가 긴 공지에서 잘리지 않게 넉넉히 둔다.
                .maxTokens(opus ? 12_000L : 4_000L)
                .system(PROMPT);
        // 작은 모델은 옮겨 적기라 생각을 끄면 출력 토큰이 준다. Opus 5.5는 생각을 끌 수 없어 그대로 둔다.
        if (!opus) {
            b.thinking(ThinkingConfigDisabled.builder().build());
        }
        return b.outputConfig(StructuredOutputConfig.<LocatorReading>builder()
                        .format(LocatorReading.class, JsonSchemaLocalValidation.YES).build())
                .addUserMessage(userText(text, postedOn))
                .build();
    }

    private synchronized AnthropicClient client() {
        if (client == null) {
            client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        }
        return client;
    }
}
