package com.campusalimi.domain;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginTokenRepository extends JpaRepository<LoginToken, Long> {

    Optional<LoginToken> findByTokenHash(String tokenHash);

    void deleteByEmail(String email);

    long countByEmailAndCreatedAtAfter(String email, OffsetDateTime since);

    long countByCreatedAtAfter(OffsetDateTime since);
}
