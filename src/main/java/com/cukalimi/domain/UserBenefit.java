package com.cukalimi.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "user_benefit")
@IdClass(UserBenefit.Key.class)
public class UserBenefit {

    @Id
    private Long userId;
    @Id
    private Long benefitId;
    private OffsetDateTime checkedAt = OffsetDateTime.now();

    protected UserBenefit() {
    }

    public UserBenefit(Long userId, Long benefitId) {
        this.userId = userId;
        this.benefitId = benefitId;
    }

    public Long getBenefitId() {
        return benefitId;
    }

    public static class Key implements Serializable {
        private Long userId;
        private Long benefitId;

        public Key() {
        }

        public Key(Long userId, Long benefitId) {
            this.userId = userId;
            this.benefitId = benefitId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(benefitId, k.benefitId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, benefitId);
        }
    }
}
