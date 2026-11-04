package com.cukalimi.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoneItemRepository extends JpaRepository<DoneItem, DoneItem.Key> {

    List<DoneItem> findByUserId(Long userId);

    boolean existsByDateItemId(Long dateItemId);

    void deleteByUserId(Long userId);
}
