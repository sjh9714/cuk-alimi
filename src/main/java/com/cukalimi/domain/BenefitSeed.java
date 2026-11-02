package com.cukalimi.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/** benefits.yml의 항목 하나. 필드 이름은 파일의 키와 같다. 파일에 있는 항목은 전부 활성이다. */
public record BenefitSeed(
        String slug,
        String name,
        String category,
        String summary,
        String condition,
        @JsonProperty("how_to") String howTo,
        String price,
        @JsonProperty("source_name") String sourceName,
        @JsonProperty("source_url") String sourceUrl,
        @JsonProperty("verified_on") LocalDate verifiedOn,
        @JsonProperty("renewal_rule") String renewalRule,
        @JsonProperty("ends_on") LocalDate endsOn) {
}
