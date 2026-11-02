package com.cukalimi.notice;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import com.cukalimi.config.AppProperties;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Claude로 그림을 받아 적는다. 2026-10-29 시험에서 그림 1장에 약 $0.0007이었다. */
@Component
public class ClaudeImageTranscriber implements ImageTranscriber {

    /** 시험한 문구 그대로다. 바꾸면 docs/verification/2026-10-29-image-ocr.md의 시험을 다시 돌린다. */
    static final String PROMPT = "이 그림은 대학교 공지에 붙은 그림이다. 그림 속 글자를 빠짐없이 그대로 받아 적어라. "
            + "요약하거나 해석하거나 고치지 마라. 읽을 수 없는 글자는 [?]로 적어라. "
            + "결과는 HTML 조각으로만 내라. 문단과 줄은 <p>, 표는 <table><tr><td>로 행과 칸을 그대로 옮겨라. "
            + "다른 설명이나 코드 블록 표시는 붙이지 마라.";

    private final AppProperties.Transcribe config;
    private AnthropicClient client;

    public ClaudeImageTranscriber(AppProperties props) {
        this.config = props.transcribe();
    }

    @Override
    public boolean enabled() {
        return config != null && config.enabled();
    }

    @Override
    public Result transcribe(byte[] image, String mediaType) {
        Message message;
        try {
            message = client().messages().create(params(image, mediaType));
        } catch (com.anthropic.errors.BadRequestException | com.anthropic.errors.UnprocessableEntityException e) {
            throw new Rejected(e.getMessage(), e);
        }
        StopReason stop = message.stopReason().orElse(null);
        if (!StopReason.END_TURN.equals(stop)) {
            throw new ChargedFailure("받아 적기가 끝까지 가지 않았습니다: " + stop, message.usage().inputTokens(),
                    message.usage().outputTokens());
        }
        String text = message.content().stream().flatMap(b -> b.text().stream()).map(TextBlock::text)
                .collect(Collectors.joining());
        return new Result(text, config.model(), message.usage().inputTokens(), message.usage().outputTokens());
    }

    private MessageCreateParams params(byte[] image, String mediaType) {
        return MessageCreateParams.builder()
                .model(config.model())
                .maxTokens(8000L)
                // 글자를 옮기는 일이라 생각할 거리가 없다. 끄면 출력 토큰이 준다.
                .thinking(ThinkingConfigDisabled.builder().build())
                .addUserMessageOfContentBlockParams(List.of(
                        ContentBlockParam.ofImage(ImageBlockParam.builder()
                                .source(Base64ImageSource.builder()
                                        .data(Base64.getEncoder().encodeToString(image))
                                        .mediaType(Base64ImageSource.MediaType.of(mediaType))
                                        .build())
                                .build()),
                        ContentBlockParam.ofText(TextBlockParam.builder().text(PROMPT).build())))
                .build();
    }

    private synchronized AnthropicClient client() {
        if (client == null) {
            client = AnthropicOkHttpClient.builder().apiKey(config.apiKey()).build();
        }
        return client;
    }
}
