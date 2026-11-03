package com.cukalimi.domain;

import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageTranscriptRepository extends JpaRepository<ImageTranscript, String> {

    /** 하루 상한은 요금이 나간 호출만 센다. 건너뛴 그림(토큰 0)은 세지 않는다. */
    long countByCreatedAtAfterAndInputTokensGreaterThan(OffsetDateTime since, long tokens);

    /** 품질 페이지용 합계. 받아 적은 글(text)은 읽지 않는다. */
    @org.springframework.data.jpa.repository.Query("select count(t) as images, coalesce(sum(t.inputTokens), 0) as inputTokens, "
            + "coalesce(sum(t.outputTokens), 0) as outputTokens from ImageTranscript t where t.inputTokens > 0")
    Usage usage();

    long countByInputTokens(long tokens);

    /** 공지 한 건의 받아 적은 글. AI 대상 읽기가 그림 공지의 글로 쓴다. */
    java.util.List<ImageTranscript> findByArticleNo(Long articleNo);

    interface Usage {
        long getImages();

        long getInputTokens();

        long getOutputTokens();
    }
}
