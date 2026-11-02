package com.cukalimi.benefit;

import com.cukalimi.domain.Benefit;
import com.cukalimi.domain.BenefitRepository;
import com.cukalimi.domain.BenefitSeed;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** benefits.yml 내용을 benefit 표에 맞춘다. 한 트랜잭션. 파일에서 빠진 항목은 지우지 않고 비활성으로 둔다. */
@Service
public class BenefitStore {

    private final BenefitRepository benefits;

    public BenefitStore(BenefitRepository benefits) {
        this.benefits = benefits;
    }

    public record Result(int total, int inserted, int updated, int deactivated) {
    }

    @Transactional
    public Result seed(List<BenefitSeed> seeds) {
        int inserted = 0;
        int updated = 0;
        Set<String> seen = new HashSet<>();
        for (BenefitSeed seed : seeds) {
            seen.add(seed.slug());
            Benefit benefit = benefits.findBySlug(seed.slug()).orElse(null);
            if (benefit == null) {
                benefit = new Benefit(seed.slug());
                benefit.apply(seed);
                benefits.save(benefit);
                inserted++;
            } else if (benefit.apply(seed)) {
                updated++;
            }
        }
        int deactivated = 0;
        for (Benefit b : benefits.findAll()) {
            if (!seen.contains(b.getSlug()) && b.deactivate()) {
                deactivated++;
            }
        }
        return new Result(seeds.size(), inserted, updated, deactivated);
    }
}
