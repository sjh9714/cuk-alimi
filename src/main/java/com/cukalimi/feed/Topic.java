package com.cukalimi.feed;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 공지의 주제 (ADR 014). 형광펜은 학생이 고른 관심 주제 가운데 자격이 되는 일에만 칠한다.
 * 제목으로 정하고, 아래 RULES의 순서가 우선순위다. "겨울계절학기 학점교류"는 학점교류이고, "국가근로장학금"은 장학금이 아니라 근로·인턴장학이다.
 * 규칙은 2026-11-04 학교 게시판 목록 조사에서 쓴 정규식을 옮겼다(`docs/research/2026-11-04-interest-by-views.md`).
 */
public enum Topic {
    EXCHANGE("exchange", "학점교류"),
    GRADUATION("graduation", "졸업"),
    SEASONAL("seasonal", "계절학기"),
    GRAD("grad", "대학원"),
    WORKSTUDY("workstudy", "근로·인턴장학"),
    COURSE("course", "수강·학적"),
    SCHOLARSHIP("scholarship", "장학금"),
    CAREER("career", "취업·채용"),
    EXTRACURRICULAR("extracurricular", "비교과·대회·특강"),
    OTHER("other", "기타");

    /**
     * 위에서부터 처음 맞는 규칙이 주제다. 같은 주제가 두 번 나오는 것은 강한 말과 약한 말을 나눠서다.
     * 예를 들어 "공모전"은 취업 게시판 글이어도 비교과이고, "프로그램"은 취업 말이 먼저 걸리면 취업이다.
     * 장학은 "등록금 지원", "성적우수장학"이 수강·학적의 "등록", "성적"보다 먼저 걸려야 해서 수강·학적 앞에 둔다.
     */
    private record Rule(Topic topic, Pattern pattern) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(EXCHANGE, Pattern.compile("학점교류|CU12|공유대학")),
            new Rule(GRADUATION, Pattern.compile("졸업|학위\\s*수여|이수여부|수료")),
            new Rule(SEASONAL, Pattern.compile("계절\\s*학기")),
            new Rule(WORKSTUDY, Pattern.compile("국가근로|근로\\s*장학|집중근로|희망근로지|교육지원인턴|직무인턴|연구인턴")),
            new Rule(SCHOLARSHIP, Pattern.compile("장학|학자금|등록금\\s*(전액\\s*)?지원|이자\\s*지원|드림클래스|서울런|파란사다리|위피스쿨|우수입학생|지역돌봄")),
            new Rule(GRAD, Pattern.compile("대학원|학석사")),
            new Rule(EXTRACURRICULAR, Pattern.compile("공모전|해커톤|대회|서포터즈|축제|간식|캠프")),
            new Rule(COURSE, Pattern.compile(
                    "수강|휴학|복학|등록|성적|공결|출결|수업\\s*평가|전과|복수전공|부전공|융합전공|학생설계전공|재입학|자퇴|제적|시간표|강의\\s*변경|강의\\s*시간"
                            + "|교과목|과목|전공\\s*(예비\\s*)?선택|전공\\s*배정|학점\\s*인정|폐강|제한인원|수업\\s*운영|학사\\s*일정|개강|종강")),
            new Rule(CAREER, Pattern.compile("채용|대플|인턴|취업|일경험|일자리|현장실습|기업|직무|커리어|멘토링|공채|면접|해외진출|전문인력")),
            new Rule(EXTRACURRICULAR, Pattern.compile("특강|프로그램|설명회|교육|봉사|동아리|챌린지|이벤트|세미나|강연|워크숍|아카데미|Week|창업|행사|페스타|포럼|간담회")));

    /** 제목 앞의 "[대학일자리플러스센터]", "(한국장학재단)" 같은 머리말은 부서 이름이라 주제가 아니다. */
    private static final Pattern PREFIX = Pattern.compile("^\\s*(\\[[^\\]]*\\]|\\([^)]*\\)|<[^>]*>|\\s)+");

    /**
     * 고르기 전 기본값. 학교 게시판 조회수 중앙값이 전체의 1.5배 이상인 다섯 주제다(2025-11~2026-10 게시 443건).
     * 학점교류(1.16), 비교과(1.27), 취업·채용(0.66), 대학원(0.80)은 기본으로 켜지 않는다.
     */
    public static final Set<Topic> DEFAULTS = EnumSet.of(COURSE, SEASONAL, SCHOLARSHIP, WORKSTUDY, GRADUATION);

    private final String key;
    private final String label;

    Topic(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    /** 제목 하나의 주제. 아무 규칙에도 안 걸리면 기타. */
    public static Topic of(String title) {
        if (title == null) {
            return OTHER;
        }
        // 머리말을 뗀 제목으로 먼저 보고, 아무것도 안 걸리면 머리말까지 본다("[대플-추천채용] JB주식회사 … 모집"은 머리말이 주제다).
        Topic body = match(PREFIX.matcher(title).replaceFirst(""));
        return body != OTHER ? body : match(title);
    }

    private static Topic match(String text) {
        for (Rule r : RULES) {
            if (r.pattern().matcher(text).find()) {
                return r.topic();
            }
        }
        return OTHER;
    }

    public static Optional<Topic> byKey(String key) {
        return Arrays.stream(values()).filter(t -> t.key.equals(key)).findFirst();
    }

    /** "course,seasonal"을 주제 모음으로. null이면 기본값, 알 수 없는 키는 IllegalArgumentException. */
    public static Set<Topic> parse(String keys) {
        if (keys == null) {
            return DEFAULTS;
        }
        Set<Topic> out = EnumSet.noneOf(Topic.class);
        for (String k : keys.split(",")) {
            if (!k.isBlank()) {
                out.add(byKey(k.trim()).orElseThrow(() -> new IllegalArgumentException("알 수 없는 관심 주제입니다: " + k)));
            }
        }
        return out;
    }

    public static String join(Set<Topic> topics) {
        return String.join(",", topics.stream().map(Topic::key).toList());
    }

    /** 고를 수 있는 주제(기타 빼고), 선언 순서. */
    public static List<Topic> choosable() {
        return Arrays.stream(values()).filter(t -> t != OTHER).toList();
    }

    public static Set<String> keys(Set<Topic> topics) {
        Set<String> out = new LinkedHashSet<>();
        topics.forEach(t -> out.add(t.key));
        return out;
    }
}
