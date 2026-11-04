package com.cukalimi.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedItemRepository extends JpaRepository<SavedItem, SavedItem.Key> {

    List<SavedItem> findByUserId(Long userId);

    boolean existsByDateItemId(Long dateItemId);

    void deleteByUserId(Long userId);
}
