package com.cukalimi.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BenefitRepository extends JpaRepository<Benefit, Long> {

    Optional<Benefit> findBySlug(String slug);

    List<Benefit> findByActiveTrueOrderByCategoryAscNameAsc();
}
