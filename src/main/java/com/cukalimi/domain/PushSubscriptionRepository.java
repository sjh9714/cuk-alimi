package com.cukalimi.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    List<PushSubscription> findByUserId(Long userId);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    long countByUserId(Long userId);

    void deleteByUserId(Long userId);

    @Query("select distinct s.userId from PushSubscription s")
    List<Long> findDistinctUserIds();
}
