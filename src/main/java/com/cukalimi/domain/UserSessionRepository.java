package com.cukalimi.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);

    void deleteByUserId(Long userId);
}
