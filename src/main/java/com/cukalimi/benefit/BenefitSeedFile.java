package com.cukalimi.benefit;

import com.cukalimi.domain.BenefitSeed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.regex.Pattern;

/** benefits.yml을 읽고, 규칙(출처·확인일·갱신 규칙이 전부 있을 것)을 지키는지 검사한다. */
public final class BenefitSeedFile {

    public static final String PATH = "/benefits.yml";
    private static final Pattern SLUG = Pattern.compile("[a-z0-9-]{3,64}");
    private static final List<String> CATEGORIES =
            List.of("TOOL", "SUBSCRIPTION", "TRANSPORT", "FINANCE", "SCHOLARSHIP", "SCHOOL", "CULTURE");

    private BenefitSeedFile() {
    }

    public static List<BenefitSeed> load() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        try (InputStream in = BenefitSeedFile.class.getResourceAsStream(PATH)) {
            if (in == null) {
                throw new IllegalStateException(PATH + " 이 없습니다");
            }
            List<BenefitSeed> seeds = mapper.readValue(in, mapper.getTypeFactory()
                    .constructCollectionType(List.class, BenefitSeed.class));
            validate(seeds);
            return seeds;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void validate(List<BenefitSeed> seeds) {
        java.util.Set<String> slugs = new java.util.HashSet<>();
        for (BenefitSeed s : seeds) {
            require(s.slug() != null && SLUG.matcher(s.slug()).matches(), s, "slug는 소문자·숫자·하이픈 3~64자");
            require(slugs.add(s.slug()), s, "slug 중복");
            require(notBlank(s.name()), s, "name 없음");
            require(s.category() != null && CATEGORIES.contains(s.category()), s, "category는 " + CATEGORIES);
            require(notBlank(s.summary()) && s.summary().length() <= 100, s, "summary는 1~100자");
            require(notBlank(s.condition()), s, "condition 없음");
            require(notBlank(s.howTo()), s, "how_to 없음");
            require(notBlank(s.sourceName()), s, "source_name 없음");
            require(s.sourceUrl() != null && s.sourceUrl().startsWith("https://"), s, "source_url은 https");
            require(s.verifiedOn() != null, s, "verified_on 없음");
            require(!s.verifiedOn().isAfter(java.time.LocalDate.now()), s, "verified_on이 미래");
            require(s.endsOn() == null || !s.endsOn().isBefore(s.verifiedOn()), s, "ends_on이 verified_on보다 앞");
            require(notBlank(s.renewalRule()), s, "renewal_rule 없음");
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static void require(boolean ok, BenefitSeed s, String message) {
        if (!ok) {
            throw new IllegalStateException("benefits.yml [" + (s.slug() == null ? s.name() : s.slug()) + "]: " + message);
        }
    }
}
