package com.cukalimi.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FetchLogRepository extends JpaRepository<FetchLog, Long> {

    long countByFetchedAtAfter(java.time.OffsetDateTime since);

    long countByFetchedAtAfterAndErrorIsNotNull(java.time.OffsetDateTime since);
}
