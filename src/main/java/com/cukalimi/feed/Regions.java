package com.cukalimi.feed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 시·도와 시·군·구 목록(regions.yml). 지역 장학 공지를 알아보고 프로필의 연고 지역과 맞춘다 (ADR 009).
 * 제목에 행정구역 이름이 "그 지역 사람"이라는 뜻으로 붙었을 때만 지역으로 본다. 대학 이름(대구가톨릭대)은 아니다.
 */
public final class Regions {

    public static final String PATH = "/regions.yml";

    public record Sido(String name, String shortName, List<String> sigungu) {
    }

    /** 시·도만 있으면 그 시·도 전체. */
    public record Region(String sido, String sigungu) {

        public String label() {
            return sigungu == null ? sido : sido + " " + sigungu;
        }

        /** 공지 지역이 사용자의 연고 지역을 덮는가. 공지가 시·도 단위면 그 안의 모든 시·군·구가 맞다. */
        public boolean covers(Region home) {
            return sido.equals(home.sido) && (sigungu == null || sigungu.equals(home.sigungu));
        }
    }

    /** 지역 이름 뒤에 붙어 "그 지역 출신·거주자"를 뜻하는 말. 춘천인재육성, 은평구민, 대전청년내일재단. */
    private static final String HOME_SUFFIX = "(?:시|군|구|도)?(?:민|인재|장학|청년|지역|출신|거주)";
    private static final String LEFT = "(?<![가-힣])";

    private static final List<Sido> ALL = load();
    private static final List<Matcher> MATCHERS = compile(ALL);

    /** 한 시·도의 이름 패턴과 그 안의 시·군·구 패턴. 요청마다 만들지 않게 한 번만 컴파일한다. */
    private record Matcher(Sido sido, Pattern named, Pattern home, Pattern sentenceSido, List<SigunguPattern> sigungu) {
    }

    private record SigunguPattern(String name, Pattern full, Pattern stem) {
    }

    private Regions() {
    }

    public static List<Sido> all() {
        return ALL;
    }

    /** "전라남도 순천시" 또는 "전라남도". 목록에 없는 값은 비운다. */
    public static Optional<Region> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String[] parts = value.trim().split("\\s+", 2);
        for (Sido s : ALL) {
            if (!s.name().equals(parts[0])) {
                continue;
            }
            if (parts.length == 1) {
                return Optional.of(new Region(s.name(), null));
            }
            return s.sigungu().contains(parts[1]) ? Optional.of(new Region(s.name(), parts[1])) : Optional.empty();
        }
        return Optional.empty();
    }

    /** 공지 제목에서 지역 조건을 찾는다. 같은 이름의 구가 여러 시·도에 있으면(중구, 고성군) 후보를 모두 돌려준다. */
    public static List<Region> inTitle(String title) {
        Set<String> sidoHits = new LinkedHashSet<>();
        for (Matcher m : MATCHERS) {
            if (m.named().matcher(title).find() || m.home().matcher(title).find()) {
                sidoHits.add(m.sido().name());
            }
        }
        List<Region> hits = new ArrayList<>();
        for (Matcher m : MATCHERS) {
            if (!sidoHits.isEmpty() && !sidoHits.contains(m.sido().name())) {
                continue;
            }
            for (SigunguPattern g : m.sigungu()) {
                if (g.full().matcher(title).find() || (g.stem() != null && g.stem().matcher(title).find())) {
                    hits.add(new Region(m.sido().name(), g.name()));
                }
            }
        }
        for (String sido : sidoHits) {
            if (hits.stream().noneMatch(r -> r.sido().equals(sido))) {
                hits.add(new Region(sido, null));
            }
        }
        return dedupe(hits);
    }

    /**
     * 대상 문장에서 지역 조건을 찾는다. "서초구에 거주", "진도군에 주민등록"처럼 거주·출신을 말하는 문장일 때만.
     * 학교 주소나 접수처 주소에 나오는 지명은 조건이 아니다.
     */
    public static List<Region> inAudienceSentence(String sentence) {
        if (sentence == null || !sentence.matches("(?s).*(거주|주민등록|출신|연고|소재 (중|고등)학교).*")) {
            return List.of();
        }
        List<Region> hits = new ArrayList<>();
        for (Matcher m : MATCHERS) {
            for (SigunguPattern g : m.sigungu()) {
                if (g.full().matcher(sentence).find()) {
                    hits.add(new Region(m.sido().name(), g.name()));
                }
            }
        }
        if (hits.isEmpty()) {
            for (Matcher m : MATCHERS) {
                if (m.sentenceSido().matcher(sentence).find()) {
                    hits.add(new Region(m.sido().name(), null));
                }
            }
        }
        return dedupe(hits);
    }

    private static List<Matcher> compile(List<Sido> all) {
        List<Matcher> out = new ArrayList<>();
        for (Sido s : all) {
            String names = Pattern.quote(s.name()) + "|" + Pattern.quote(s.shortName()) + "(?:특별|광역)?시";
            List<SigunguPattern> sigungu = new ArrayList<>();
            for (String g : s.sigungu()) {
                String stem = g.substring(0, g.length() - 1);
                sigungu.add(new SigunguPattern(g, Pattern.compile(LEFT + Pattern.quote(g)),
                        stem.length() >= 2 ? Pattern.compile(LEFT + Pattern.quote(stem) + HOME_SUFFIX) : null));
            }
            out.add(new Matcher(s,
                    Pattern.compile(LEFT + "(?:" + names + ")"),
                    Pattern.compile(LEFT + Pattern.quote(s.shortName()) + HOME_SUFFIX),
                    Pattern.compile(LEFT + "(?:" + Pattern.quote(s.name()) + "|" + Pattern.quote(s.shortName()) + "시?)(?![가-힣]*대학)"),
                    List.copyOf(sigungu)));
        }
        return List.copyOf(out);
    }

    private static List<Region> dedupe(List<Region> hits) {
        return List.copyOf(new LinkedHashSet<>(hits));
    }

    private static List<Sido> load() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (InputStream in = Regions.class.getResourceAsStream(PATH)) {
            if (in == null) {
                throw new IllegalStateException(PATH + " 이 없습니다");
            }
            List<java.util.Map<String, Object>> rows = mapper.readValue(in,
                    mapper.getTypeFactory().constructCollectionType(List.class, java.util.Map.class));
            List<Sido> out = new ArrayList<>();
            for (var row : rows) {
                @SuppressWarnings("unchecked")
                List<String> sigungu = (List<String>) row.get("sigungu");
                out.add(new Sido((String) row.get("name"), (String) row.get("short"), List.copyOf(sigungu)));
            }
            return List.copyOf(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
