package com.cukalimi.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    Optional<Notice> findByArticleNo(long articleNo);

    boolean existsByArticleNo(long articleNo);

    long countByReviewReason(String reviewReason);

    java.util.List<Notice> findByStatusOrderByPostedOnDesc(String status);

    java.util.List<Notice> findByStatusAndReviewReasonAndPostedOnGreaterThanEqual(String status, String reviewReason,
            java.time.LocalDate since);
}
