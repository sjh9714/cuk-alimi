package com.cukalimi.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    Optional<Notice> findByArticleNo(long articleNo);

    boolean existsByArticleNo(long articleNo);

    long countByReviewReason(String reviewReason);

    java.util.List<Notice> findByStatusOrderByPostedOnDesc(String status);

    java.util.List<Notice> findByCategoryAndPostedOn(String category, java.time.LocalDate postedOn);

    java.util.List<Notice> findByPostedOnGreaterThanEqualAndRemovedAtIsNull(java.time.LocalDate since);

    long countByFirstSeenAtAfter(java.time.OffsetDateTime since);

    java.util.List<Notice> findByStatusAndReviewReasonAndPostedOnGreaterThanEqual(String status, String reviewReason,
            java.time.LocalDate since);

    /** AI 대상 읽기를 기다리는 공지 (ADR 013). */
    java.util.List<Notice> findByAudienceReadAtIsNullAndPostedOnGreaterThanEqual(java.time.LocalDate since);

    /** 하루 상한. 공지 한 건에 두 번 부른다. */
    long countByAudienceReadAtAfter(java.time.OffsetDateTime since);

    /** 품질 페이지용 합계. */
    @org.springframework.data.jpa.repository.Query("select count(n) as notices, count(n.audienceAi) as agreed, "
            + "coalesce(sum(n.audienceInputTokens), 0) as inputTokens, coalesce(sum(n.audienceOutputTokens), 0) as outputTokens "
            + "from Notice n where n.audienceReadAt is not null")
    AudienceUsage audienceUsage();

    interface AudienceUsage {
        long getNotices();

        long getAgreed();

        long getInputTokens();

        long getOutputTokens();
    }
}
