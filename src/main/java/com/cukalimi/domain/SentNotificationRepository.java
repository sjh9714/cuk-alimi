package com.cukalimi.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SentNotificationRepository extends JpaRepository<SentNotification, SentNotification.Key> {

    List<SentNotification> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
