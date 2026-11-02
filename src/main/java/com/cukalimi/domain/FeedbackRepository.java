package com.cukalimi.domain;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findTop100ByOrderByCreatedAtDesc();

    long countByUserIdAndCreatedAtAfter(Long userId, OffsetDateTime since);

    void deleteByUserId(Long userId);
}
