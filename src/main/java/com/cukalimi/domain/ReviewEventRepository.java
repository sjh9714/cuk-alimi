package com.cukalimi.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewEventRepository extends JpaRepository<ReviewEvent, Long> {

    List<ReviewEvent> findByNoticeIdOrderByIdAsc(Long noticeId);

    boolean existsByNoticeIdAndAction(Long noticeId, String action);
}
