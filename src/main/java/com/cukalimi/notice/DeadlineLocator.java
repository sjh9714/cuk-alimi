package com.cukalimi.notice;

import java.time.LocalDate;

/**
 * 공지 글에서 날짜가 적힌 줄을 찾아 글자 그대로 옮긴다 (ADR 016). 날짜를 읽지는 않는다.
 * 옮겨 온 줄이 원문에 있는지 확인하고 날짜를 읽는 일은 코드(QuoteCheck)가 한다.
 */
public interface DeadlineLocator {

    record Result(LocatorReading reading, String model, long inputTokens, long outputTokens) {
    }

    boolean enabled();

    /** 기록과 하루 상한에 쓰는 모델 이름. */
    String model();

    /**
     * 실패하면 예외를 던진다. Failed는 요금이 나갔거나 다시 해도 소용없는 실패라 기록하고 다시 부르지 않는다.
     * 그 밖의 예외(과부하, 요청 한도, 네트워크)는 다음 차례에 다시 시도한다.
     */
    Result locate(NoticeText text, LocalDate postedOn);

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
