package com.cukalimi.notice;

/** 공지 그림 한 장의 글자를 그대로 옮겨 적는다. 요약하거나 날짜를 판단하지 않는다 (ADR 010). */
public interface ImageTranscriber {

    record Result(String text, String model, long inputTokens, long outputTokens) {
    }

    boolean enabled();

    /**
     * 실패하면 예외를 던진다. Rejected와 ChargedFailure는 다시 해도 소용없어 기록하고 다시 부르지 않는다.
     * 그 밖의 예외(과부하, 요청 한도, 네트워크)는 요금이 없는 일시 오류라 다음 차례에 다시 시도한다.
     */
    Result transcribe(byte[] image, String mediaType);

    /** 이 그림은 다시 보내도 받지 않는다(잘못된 형식 등). 요금은 없다. */
    class Rejected extends RuntimeException {
        public Rejected(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** 요금이 나간 뒤 끝까지 적지 못했다. 쓴 토큰을 함께 넘겨 비용에 센다. */
    class ChargedFailure extends RuntimeException {
        private final long inputTokens;
        private final long outputTokens;

        public ChargedFailure(String message, long inputTokens, long outputTokens) {
            super(message);
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
