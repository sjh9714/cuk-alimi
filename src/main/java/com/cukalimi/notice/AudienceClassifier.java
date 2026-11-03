package com.cukalimi.notice;

import com.cukalimi.feed.AudienceFacts;
import com.cukalimi.feed.AudienceReading;

/** 공지 글에서 지원 대상 조건을 옮겨 적는다. 내 프로필과 맞는지는 판단하지 않는다 (ADR 013). */
public interface AudienceClassifier {

    /** facts는 비교에 쓰는 조건, reading은 AI가 옮겨 적은 그대로(측정 기록용, 없을 수 있음). */
    record Result(AudienceFacts facts, String model, long inputTokens, long outputTokens, AudienceReading reading) {
        public Result(AudienceFacts facts, String model, long inputTokens, long outputTokens) {
            this(facts, model, inputTokens, outputTokens, null);
        }
    }

    boolean enabled();

    /**
     * 실패하면 예외를 던진다. Failed는 요금이 나갔거나 다시 해도 소용없는 실패라 기록하고 다시 부르지 않는다.
     * 그 밖의 예외(과부하, 요청 한도, 네트워크)는 다음 차례에 다시 시도한다.
     */
    Result classify(String title, String text);

    class Failed extends RuntimeException {
        private final long inputTokens;
        private final long outputTokens;

        public Failed(String message, long inputTokens, long outputTokens, Throwable cause) {
            super(message, cause);
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
        }

        public long inputTokens() {
            return inputTokens;
        }

        public long outputTokens() {
            return outputTokens;
        }
    }
}
