package com.cukalimi.ops;

/**
 * AI 호출 단가. 달러 / 100만 토큰이고 2026-11 가격표 기준이다. 가격이 바뀌면 여기 한 곳만 고친다.
 * 모르는 모델은 가장 비싼 값으로 셈한다. 예산 가드가 모자라게 세는 것보다 넉넉하게 세는 편이 안전하다.
 */
public final class AiPrices {

    private AiPrices() {
    }

    record Price(double in, double out) {
    }

    private static final Price HAIKU = new Price(0.10, 0.50);
    private static final Price OPUS = new Price(4.00, 20.00);

    /** model은 "claude-haiku-5-5"나, 어긋난 읽기를 뜻하는 "disagree:claude-haiku-5-5" 꼴이다. */
    public static double usd(String model, long inputTokens, long outputTokens) {
        String name = model == null ? "" : model.substring(model.lastIndexOf(':') + 1);
        Price p = name.startsWith("claude-haiku") ? HAIKU : OPUS;
        return (inputTokens * p.in() + outputTokens * p.out()) / 1_000_000.0;
    }
}
