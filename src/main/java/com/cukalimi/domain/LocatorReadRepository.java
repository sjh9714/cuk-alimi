package com.cukalimi.domain;

import com.cukalimi.ops.AiBudget;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocatorReadRepository extends JpaRepository<LocatorRead, Long> {

    List<LocatorRead> findByNoticeIdOrderByIdAsc(Long noticeId);

    /** 그때부터 읽은 공지 수. 하루 상한에 쓴다. */
    @Query("select count(distinct r.noticeId) from LocatorRead r where r.createdAt >= :since")
    long countNoticesSince(@Param("since") OffsetDateTime since);

    /** 그때부터 그 모델을 부른 횟수. 큰 모델의 하루 상한에 쓴다. */
    long countByModelAndCreatedAtGreaterThanEqual(String model, OffsetDateTime since);

    /** 그때부터 쓴 토큰을 모델별로. 예산 가드가 이번 달 비용을 센다. */
    @Query("select r.model as model, coalesce(sum(r.inputTokens), 0) as inputTokens, coalesce(sum(r.outputTokens), 0) as outputTokens "
            + "from LocatorRead r where r.createdAt >= :since group by r.model")
    List<AiBudget.Usage> usageSince(@Param("since") OffsetDateTime since);
}
