package com.cukalimi.notice;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * 문장 찾기 AI의 답 (ADR 016). 날짜가 적힌 줄을 원문 그대로 옮긴 것의 목록이다. 날짜를 적는 칸은 없다.
 * AI는 "어디에 적혀 있는지"만 가리키고, 날짜는 코드가 그 줄에서 읽는다 (QuoteCheck).
 *
 * @param found 찾은 줄들. 없으면 빈 목록
 */
public record LocatorReading(
        @JsonPropertyDescription(FOUND)
        List<Found> found) {

    static final String FOUND = "날짜가 적힌 줄들. 없으면 빈 목록";
    static final String QUOTE = "공지의 한 줄이나 표의 한 행을 글자 그대로 옮긴 것. 날짜 글자가 들어 있어야 한다";
    static final String WHAT = "그날까지 무엇을 하는지 짧게(\"신청\", \"서류 제출\"). 날짜나 숫자는 넣지 않는다";
    static final String ACTION = "학생이 그날까지 신청·제출·납부·등록을 해야 하면 true. 발표, 시험일, 행사, 수업기간, 부서의 처리 단계면 false";

    /**
     * @param quote  공지의 한 줄이나 표의 한 행을 글자 그대로 옮긴 것. 날짜 글자가 들어 있어야 한다
     * @param what   그날까지 무엇을 하는지 짧게("신청", "서류 제출"). 날짜나 숫자는 넣지 않는다
     * @param action 학생이 그날까지 신청·제출·납부·등록을 해야 하면 true. 발표, 시험일, 행사, 수업기간, 부서의 처리 단계면 false
     */
    public record Found(
            @JsonPropertyDescription(QUOTE)
            String quote,
            @JsonPropertyDescription(WHAT)
            String what,
            @JsonPropertyDescription(ACTION)
            boolean action) {
    }

    public LocatorReading {
        found = found == null ? List.of() : List.copyOf(found);
    }
}
