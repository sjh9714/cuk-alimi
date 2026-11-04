package com.cukalimi.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByUnsubscribeToken(String token);

    Optional<AppUser> findByCalendarToken(String token);

    java.util.List<AppUser> findByDigestEnabledTrue();
}
