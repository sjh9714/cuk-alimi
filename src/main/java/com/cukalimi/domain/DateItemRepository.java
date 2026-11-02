package com.cukalimi.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DateItemRepository extends JpaRepository<DateItem, Long> {

    Optional<DateItem> findByNaturalKey(String naturalKey);

    List<DateItem> findByNoticeIdOrderByEndsOnAsc(Long noticeId);

    List<DateItem> findByNaturalKeyStartingWith(String prefix);

    List<DateItem> findByEndsOnGreaterThanEqual(java.time.LocalDate day);

    /** 학생 화면용. 그림에서 읽은 후보(CANDIDATE)는 운영자가 승인하기 전까지 빼고 본다. */
    List<DateItem> findByEndsOnGreaterThanEqualAndStatusNot(java.time.LocalDate day, String status);

    List<DateItem> findByStatusNot(String status);

    long countBySourceKind(String sourceKind);

    boolean existsByNoticeId(Long noticeId);

    List<DateItem> findByNoticeIdIn(java.util.Collection<Long> noticeIds);

    /** 아직 지나지 않은 날짜가 status인 공지. 30일이 지나도 후보 날짜가 남은 공지를 놓치지 않으려고 쓴다. */
    @org.springframework.data.jpa.repository.Query("select distinct d.noticeId from DateItem d where d.status = :status and d.endsOn >= :day")
    java.util.Set<Long> findNoticeIdsWithOpenItems(@org.springframework.data.repository.query.Param("status") String status,
            @org.springframework.data.repository.query.Param("day") java.time.LocalDate day);

    long countByStatus(String status);

    /** 아직 지나지 않은 날짜가 status인 공지 수. 운영자의 승인을 기다리는 그림 공지를 센다. */
    @org.springframework.data.jpa.repository.Query("select count(distinct d.noticeId) from DateItem d where d.status = :status and d.endsOn >= :day")
    long countNoticesWithOpenItems(@org.springframework.data.repository.query.Param("status") String status,
            @org.springframework.data.repository.query.Param("day") java.time.LocalDate day);

    /** 주어진 공지 가운데 status가 아닌 날짜 항목이 하나라도 있는 공지. */
    @org.springframework.data.jpa.repository.Query("select distinct d.noticeId from DateItem d where d.noticeId in :ids and d.status <> :status")
    java.util.Set<Long> findNoticeIdsWithItemsNotIn(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids,
            @org.springframework.data.repository.query.Param("status") String status);
}
