package com.cukalimi.domain;

import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DateReportRepository extends JpaRepository<DateReport, Long> {

    boolean existsByDateItemIdAndUserId(Long dateItemId, Long userId);

    long countByUserIdAndCreatedAtAfter(Long userId, OffsetDateTime since);

    void deleteByUserId(Long userId);
}
