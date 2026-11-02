package com.cukalimi.domain;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LoginTokenRepository extends JpaRepository<LoginToken, Long> {

    Optional<LoginToken> findByTokenHash(String tokenHash);

    void deleteByEmail(String email);

    long countByEmailAndCreatedAtAfter(String email, OffsetDateTime since);

    long countByCreatedAtAfter(OffsetDateTime since);

    /** 로그인 요청을 한 번에 하나씩 세게 한다. 트랜잭션이 끝나면 풀린다. */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(771001)) l", nativeQuery = true)
    int lockLoginRequests();
}
