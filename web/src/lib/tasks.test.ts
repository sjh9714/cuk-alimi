import { describe, expect, it } from "vitest";
import { DEFAULT_TOPICS, type Card, type Line, type Section } from "./api";
import { boardCards, cleanTitle, dayText, eventsOf, groupByWhen, partsText, schoolName, splitByInterest, startsOf, tasksOf, topicParts } from "./tasks";

const TODAY = "2026-11-02";
let nextId = 1;

function line(endsOn: string, label: string | null, extra: Partial<Line> = {}): Line {
  return { id: nextId++, label, startsOn: null, endsOn, startsAt: null, endsAt: null, kind: "ACTION", saved: false, done: false, fromImage: false, ...extra };
}

function card(title: string, lines: Line[], extra: Partial<Card> = {}): Card {
  return {
    key: title,
    kind: "academic",
    title,
    sourceTitle: title,
    url: "https://example/1",
    audience: { scope: "GENERAL", label: null, excerpt: null, grade: null },
    mine: false,
    mineReason: null,
    deadline: lines[0] ?? null,
    next: lines.slice(1),
    ...extra,
  };
}

const EXCHANGE = "2026학년도 겨울계절학기 서울 및 경인지역 대학교 학점교류 안내";

describe("cleanTitle", () => {
  it("머리말, 연도, 끝의 안내를 덜어낸다", () => {
    expect(cleanTitle("2026학년도 2학기 연구인턴 신청 안내")).toBe("2학기 연구인턴 신청");
    expect(cleanTitle("(한국장학재단) 2026-2학기 국가근로장학금 추가신청기간 안내")).toBe("2학기 국가근로장학금 추가신청기간");
    expect(cleanTitle("<한국고등교육재단> 2027년 인재림 제7기 장학생 선발 안내")).toBe("한국고등교육재단 인재림 제7기 장학생 선발");
    expect(cleanTitle("[한국장학재단]2026년 2학기 푸른등대 기부장학금(2차) 신청 안내")).toBe("2학기 푸른등대 기부장학금(2차) 신청");
  });

  it("두 줄을 넘지 않게 낫표, 가운데 연도, 숫자 없는 괄호, 끝의 공고·알림·운영을 덜어낸다", () => {
    expect(cleanTitle("[인재개발처] 「포항시-글로벌녹색성장기구(GGGI) 청년 단기 행정인턴」 참가자 모집 공고")).toBe(
      "포항시-글로벌녹색성장기구 청년 단기 행정인턴 참가자 모집",
    );
    expect(cleanTitle("[대학일자리플러스센터] 인천글로벌캠퍼스운영재단 미래내일 일경험(공공행정 인턴형) 지원자 모집 안내")).toBe(
      "인천글로벌캠퍼스운영재단 미래내일 일경험 지원자 모집",
    );
    expect(cleanTitle("한국고용정보원 2026년 대학일자리플러스센터 만족도 조사 참여")).toBe("한국고용정보원 대학일자리플러스센터 만족도 조사 참여");
    expect(cleanTitle("대학일자리플러스센터 홈페이지 오픈 기념 이벤트 운영")).toBe("대학일자리플러스센터 홈페이지 오픈 기념 이벤트");
    expect(cleanTitle("8월 졸업대상자 융복합트랙 이수여부 확인 기간 알림")).toBe("8월 졸업대상자 융복합트랙 이수여부 확인 기간");
    // 숫자가 든 괄호는 남긴다.
    expect(cleanTitle("2학기 푸른등대 기부장학금(2차) 신청")).toBe("2학기 푸른등대 기부장학금(2차) 신청");
  });

  it("끝의 괄호 설명을 지운다", () => {
    expect(cleanTitle("2026학년도 2학기 공결 신청 안내(학생용 공결 안내 및 가이드 탑재 / 공결 신청 시 필독)")).toBe("2학기 공결 신청");
  });
});

describe("schoolName", () => {
  it("학교 이름을 줄인다", () => {
    expect(schoolName("한성대학교 · 서울")).toBe("한성대");
    expect(schoolName("동덕여자대학교 · 서울")).toBe("동덕여대");
    expect(schoolName("한양대학교 (안산ERICA) · 경인")).toBe("한양대 ERICA");
    expect(schoolName("경희대학교 (수원 국제) · 경인")).toBe("경희대");
    expect(schoolName("신청기간")).toBeNull();
  });
});

describe("tasksOf", () => {
  it("같은 공지의 같은 날 학교들을 한 줄로 합친다", () => {
    const c = card(EXCHANGE, [line("2026-11-02", "한성대학교 · 서울"), line("2026-11-02", "한신대학교 · 경인"), line("2026-11-03", "동덕여자대학교 · 서울")]);
    const tasks = tasksOf([c], TODAY);
    expect(tasks.map((t) => t.title)).toEqual(["한성대, 한신대 학점교류 신청", "동덕여대 학점교류 신청"]);
  });

  it("추천채용은 회사 이름으로 쓰고 같은 날 줄을 합친다", () => {
    const c = card("[대플-추천채용] 모두투어 - 채용 연계형 인턴 모집", [
      line("2026-11-06", "서류 제출 확인 및 추천자 명단 발송을 위해"),
      line("2026-11-06", "추천자 마감일"),
    ]);
    const tasks = tasksOf([c], TODAY);
    expect(tasks).toHaveLength(1);
    expect(tasks[0].title).toBe("모두투어 추천 신청");
    expect(tasks[0].from).toBe("대플 추천채용");
    expect(tasksOf([card("[대플-추천채용] 키엔스코리아(주) - 컨설팅 세일즈 신입 채용", [line("2026-11-04", "추천자 마감일")])], TODAY)[0].title).toBe(
      "키엔스코리아 추천 신청",
    );
    expect(tasksOf([card("이노진 – 품질관리 및 제형업무 채용", [line("2026-11-12", "추천자 마감일")])], TODAY)[0].title).toBe("이노진 추천 신청");
  });

  it("추천채용 회사 이름에서 ㈜와 직무를 떼고, 단계가 다른 마감은 단계로 구별한다", () => {
    const sam = card("[대플-추천채용] ㈜삼원페이퍼-영업 사원 모집", [line("2026-10-03", "자소서 제출"), line("2026-10-04", "추천 의뢰 기간")]);
    expect(tasksOf([sam], TODAY).map((t) => t.title)).toEqual(["삼원페이퍼 자소서 제출", "삼원페이퍼 추천 신청"]);
    const pos = card("[대플-추천채용] ㈜포스뱅크 – 신입사원 공개채용", [line("2026-09-30", "추천서 이메일 접수")]);
    expect(tasksOf([pos], TODAY)[0].title).toBe("포스뱅크 추천서 제출");
    const jb = card("[대플-추천채용] JB주식회사(구 중부도시가스) – 도시가스 안전관리 모집", [line("2026-09-30", "추천마감")]);
    expect(tasksOf([jb], TODAY)[0].title).toBe("JB 추천 신청");
  });

  it("추천채용이 아닌 줄과 같은 날이어도 추천 신청 한 행이다", () => {
    const c = card("이노진 – 품질관리 및 제형업무 채용", [line("2026-11-12", "입사지원서 제출"), line("2026-11-12", "추천마감")]);
    expect(tasksOf([c], TODAY).map((t) => t.title)).toEqual(["이노진 추천 신청"]);
  });

  it("꺾쇠 안의 재단 이름은 남긴다", () => {
    expect(cleanTitle("[학생지원팀] <아산사회복지재단> 2027년도 북한이탈청소년 장학생 선발 안내(~10.26)")).toBe(
      "아산사회복지재단 북한이탈청소년 장학생 선발",
    );
    expect(cleanTitle("[취업지원팀] <특강> 면접 준비 안내")).toBe("면접 준비");
  });

  it("가운데 괄호를 떼도 낱말이 붙지 않는다", () => {
    expect(cleanTitle("[학생지원팀] 2026년도 하반기 (재)논산시장학회 학업장려 장학생 선발 안내")).toBe("하반기 논산시장학회 학업장려 장학생 선발");
  });

  it("장학 공지의 추천서는 추천채용이 아니다", () => {
    const t = tasksOf([card("2026학년도 교외장학생 선발 안내", [line("2026-11-08", "학과장 추천서 제출")])], TODAY)[0];
    expect(t.title).toBe("학과장 추천서 제출");
  });

  it("뜻이 있는 라벨은 살리고 뜻 없는 말은 버린다", () => {
    const t = tasksOf([card("2026학년도 2학기 연구인턴 신청 안내", [line("2026-11-08", "신청기간 · 트리니티 신청 및 근로지 자기소개서 제출")])], TODAY)[0];
    expect(t.title).toBe("트리니티 신청 및 근로지 자기소개서 제출");
    expect(t.from).toBe("2학기 연구인턴 신청");
  });

  it("라벨이 뜻이 없으면 공문 제목을 줄여 쓴다", () => {
    for (const label of ["신청기간", "마감 기한", "내용", "일정", null]) {
      const t = tasksOf([card("2026-2학기 학생설계전공 신규·변경·포기 신청 안내", [line("2026-11-13", label)])], TODAY)[0];
      expect(t.title).toBe("2학기 학생설계전공 신규·변경·포기 신청");
    }
  });

  it("학교별 학점교류 공지의 단계 라벨에 학교 이름을 붙인다", () => {
    const c = card("2026-겨울계절학기 고려대학교 학점교류 안내", [line("2026-11-09", "신청서 접수", { endsAt: "17:00:00" })]);
    const t = tasksOf([c], TODAY)[0];
    expect(t.title).toBe("고려대 학점교류 신청서 접수");
    expect(t.at).toBe("17:00까지");
  });

  it("안내 줄은 할 일이 아니다", () => {
    const c = card("공지", [line("2026-11-09", "접수"), line("2026-11-10", "발표", { kind: "INFO" })]);
    expect(tasksOf([c], TODAY).map((t) => t.date)).toEqual(["2026-11-09"]);
  });

  it("시작일은 앞으로 열릴 때만 적는다", () => {
    const later = card("공지 가", [line("2026-11-10", "계절학기 수강신청", { startsOn: "2026-11-05" })]);
    const open = card("공지 나", [line("2026-11-10", "희망근로지 신청", { startsOn: "2026-10-30" })]);
    expect(tasksOf([later], TODAY)[0].from).toContain("11월 5일부터");
    expect(tasksOf([open], TODAY)[0].from).not.toContain("부터");
  });

  it("날짜순으로, 같은 날은 내게 맞는 공지를 먼저", () => {
    const a = card("가 공지", [line("2026-11-05", "신청")]);
    const b = card("나 공지", [line("2026-11-04", "신청")]);
    const c = card("다 공지", [line("2026-11-05", "접수")], { mine: true });
    expect(tasksOf([a, b, c], TODAY).map((t) => t.key)).toEqual(["나 공지:2026-11-04", "다 공지:2026-11-05", "가 공지:2026-11-05"]);
  });

  it("합친 줄 가운데 하나라도 저장했으면 저장됨이다", () => {
    const c = card(EXCHANGE, [line("2026-11-02", "한성대학교 · 서울"), line("2026-11-02", "한신대학교 · 경인", { saved: true })]);
    expect(tasksOf([c], TODAY)[0].saved).toBe(true);
  });
});

describe("eventsOf", () => {
  it("행사 구간만 읽고 시각을 적는다", () => {
    const ceremony = card("[학사행정팀] 2025학년도 후기 학위수여식 및 포토 스팟 안내", [line("2026-11-20", "일시", { kind: "INFO", startsAt: "14:00:00" })]);
    const sections: Section[] = [
      { key: "events", title: "행사 일정", note: "", collapsed: true, cards: [ceremony] },
      { key: "open", title: "공지", note: "", collapsed: false, cards: [card("공지", [line("2026-11-05", "신청")])] },
    ];
    expect(eventsOf(sections)).toEqual([expect.objectContaining({ title: "후기 학위수여식 및 포토 스팟", date: "2026-11-20", at: "14:00" })]);
  });

  it("행사는 할 일로 세지 않는다", () => {
    const section: Section = { key: "events", title: "행사 일정", note: "", collapsed: true, cards: [card("봄 학위수여식", [line("2027-02-18", null, { kind: "INFO" })])] };
    expect(boardCards([section])).toEqual([]);
  });
});

describe("startsOf", () => {
  const section = (cards: Card[]): Section => ({ key: "must", title: "", note: "", collapsed: false, cards });

  it("학사일정 표 항목의 앞으로 올 시작일만 적는다", () => {
    const enroll = card("수강신청", [line("2027-02-04", null, { startsOn: "2027-02-02" })], { kind: "schedule" });
    const past = card("재학생 등록기간", [line("2026-11-20", null, { startsOn: "2026-10-30" })], { kind: "schedule" });
    const notice = card("장학 신청", [line("2026-11-20", "신청", { startsOn: "2026-11-10" })], { kind: "scholarship" });
    expect(startsOf([section([enroll, past, notice])], TODAY)).toEqual([expect.objectContaining({ title: "수강신청", date: "2027-02-02", tag: "시작" })]);
  });
});

describe("boardCards", () => {
  const section = (key: Section["key"], collapsed: boolean): Section => ({ key, title: key, note: "", collapsed, cards: [card(key, [line("2026-11-05", "신청")])] });

  it("열린 구간과 취업을 올리고 지역, 대상, 날짜 확인 중은 뺀다", () => {
    const sections = [section("must", false), section("open", false), section("undated", true), section("career", true), section("region", true), section("group", true)];
    expect(boardCards(sections).map((c) => c.title)).toEqual(["must", "open", "career"]);
  });
});

describe("자리 이름뿐인 라벨", () => {
  it("모집기한, 접수 기간 같은 라벨은 공지 제목을 쓴다", () => {
    // 그림 공지(276341)의 "모집기한". 공지 제목이 안내로 읽혀 서버가 바꾸지 않은 라벨이다.
    const c = card("[학생지원팀] 2027년도 대산농촌재단 장학생 선발 안내", [line("2026-11-11", "모집기한")]);
    expect(tasksOf([c], TODAY)[0].title).toBe("대산농촌재단 장학생 선발");
    const d = card("[학생지원팀] 2027년도 대산농촌재단 장학생 선발 안내", [line("2026-11-11", "접수 기간")]);
    expect(tasksOf([d], TODAY)[0].title).toBe("대산농촌재단 장학생 선발");
  });
});

describe("tasksOf 합친 줄", () => {
  it("같은 날 줄을 합친 행은 줄 전부를 들고 있다", () => {
    const c = card("겨울계절학기 서울 및 경인지역 대학교 학점교류 안내", [line("2026-11-10", "건국대학교 · 서울"), line("2026-11-10", "상명대학교 · 서울")]);
    const [t] = tasksOf([c], TODAY);
    expect(t.lines).toHaveLength(2);
    expect(t.line).toBe(t.lines[0]);
  });
});

describe("tasksOf 긴 이름", () => {
  it("같은 날 줄이 셋 넘으면 공문 제목을 쓴다", () => {
    const c = card("[중요] 2026-2학기 졸업시험 신청 및 대체요건 제출 안내", [
      line("2026-11-27", "수상 · <심의 불필요> ACM-ICPC"),
      line("2026-11-27", "자격증 · 국가공인자격증(1급) 또는 국제공인자격증 제출자"),
      line("2026-11-27", "어학 · TOEIC 700 이상"),
    ]);
    const [t] = tasksOf([c], TODAY);
    expect(t.title).toBe("2학기 졸업시험 신청 및 대체요건 제출");
  });

  it("두 줄을 이은 이름이 길어도 공문 제목을 쓴다", () => {
    const c = card("2026학년도 겨울 계절학기 수강신청 안내", [line("2026-12-24", "환불신청 (2차) · 수업개시 이후 수강취소를 신청할 경우"), line("2026-12-24", "환불 금액 · 2/3 환불")]);
    const [t] = tasksOf([c], TODAY);
    expect(t.title).toBe("겨울 계절학기 수강신청");
  });
});

describe("splitByInterest", () => {
  const sec = (key: Section["key"], cards: Card[]): Section => ({ key, title: key, note: "", collapsed: !["must", "mine", "open"].includes(key), cards });
  const one = (title: string, topic: Card["topic"], extra: Partial<Line> = {}) => card(title, [line("2026-11-10", "신청", extra)], { topic });
  const titles = (sections: Section[], interests: readonly string[] = DEFAULT_TOPICS) => {
    const { main, outside } = splitByInterest(sections, interests, TODAY);
    return { main: main.map((t) => t.sourceTitle), outside: outside.map((t) => t.sourceTitle) };
  };

  it("기본 관심 주제 밖인 학점교류는 접고, 고르면 목록에 올린다", () => {
    const sections = [sec("open", [one("학점교류 공지", "exchange"), one("장학 공지", "scholarship")])];
    expect(titles(sections)).toEqual({ main: ["장학 공지"], outside: ["학점교류 공지"] });
    expect(titles(sections, [...DEFAULT_TOPICS, "exchange"]).outside).toEqual([]);
  });

  it("학사일정 표 항목은 주제가 기타이거나 꺼 둔 주제여도 목록에 남는다", () => {
    const sections = [sec("must", [one("중간고사", "other"), one("수강신청", "course")])];
    expect(titles(sections, []).main).toEqual(["중간고사", "수강신청"]);
  });

  it("저장한 행은 남고, 같은 공지의 다른 날 행은 접힌다", () => {
    const exchange = card("학점교류 공지", [line("2026-11-10", "서강대학교", { saved: true }), line("2026-11-12", "고려대학교")], { topic: "exchange" });
    const { main, outside } = splitByInterest([sec("open", [exchange])], DEFAULT_TOPICS, TODAY);
    expect(main.map((t) => t.date)).toEqual(["2026-11-10"]);
    expect(outside.map((t) => t.date)).toEqual(["2026-11-12"]);
  });

  it("주제가 기타이거나 없는 행은 접지 않는다", () => {
    const sections = [sec("open", [one("기타 공지", "other"), one("주제 없는 공지", null), one("주제 빠진 공지", undefined)])];
    expect(titles(sections).outside).toEqual([]);
  });

  it("취업 공지는 기본으로 접히고 취업·채용을 고르면 목록에 오른다", () => {
    const sections = [sec("career", [one("추천채용", "career")])];
    expect(titles(sections)).toEqual({ main: [], outside: ["추천채용"] });
    expect(titles(sections, ["career"]).main).toEqual(["추천채용"]);
  });

  it("지역, 특정 대상, 다른 학과, 날짜 확인 중, 행사는 어느 쪽에도 없다", () => {
    const sections = (["region", "group", "dept", "undated", "events"] as const).map((key) => sec(key, [one(key, "scholarship")]));
    expect(titles(sections)).toEqual({ main: [], outside: [] });
  });

  it("관심 주제를 모두 끄면 학사일정, 저장한 것, 기타만 남는다", () => {
    const sections = [
      sec("must", [one("수강신청", "course")]),
      sec("open", [one("장학 공지", "scholarship"), one("저장한 교류", "exchange", { saved: true }), one("기타 공지", "other")]),
    ];
    expect(titles(sections, [])).toEqual({ main: ["수강신청", "저장한 교류", "기타 공지"], outside: ["장학 공지"] });
  });
});

describe("groupByWhen", () => {
  const rows = (...dates: string[]) => dates.map((date) => ({ date }));
  const keys = (today: string, ...dates: string[]) => groupByWhen(rows(...dates), today).map((g) => [g.key, g.rows.map((r) => r.date)]);

  it("금요일에는 일요일까지가 이번 주이고 월말까지가 이달 안이다", () => {
    expect(keys("2026-11-06", "2026-11-08", "2026-11-09", "2026-11-15", "2026-11-16", "2026-11-30", "2026-12-01")).toEqual([
      ["week", ["2026-11-08"]],
      ["next", ["2026-11-09", "2026-11-15"]],
      ["month", ["2026-11-16", "2026-11-30"]],
      ["later", ["2026-12-01"]],
    ]);
  });

  it("일요일에는 내일이 다음 주다", () => {
    expect(keys("2026-11-08", "2026-11-08", "2026-11-09")).toEqual([
      ["week", ["2026-11-08"]],
      ["next", ["2026-11-09"]],
    ]);
  });

  it("다음 주가 달을 넘기면 이달 안 묶음은 없다", () => {
    expect(keys("2026-11-25", "2026-12-03", "2026-12-07")).toEqual([
      ["next", ["2026-12-03"]],
      ["later", ["2026-12-07"]],
    ]);
  });

  it("해를 넘겨도 주로 나눈다", () => {
    expect(keys("2026-12-28", "2027-01-02", "2027-01-05", "2027-01-20")).toEqual([
      ["week", ["2027-01-02"]],
      ["next", ["2027-01-05"]],
      ["later", ["2027-01-20"]],
    ]);
  });

  it("지난 날의 행은 버리고 이름을 붙인다", () => {
    const groups = groupByWhen(rows("2026-11-04", "2026-11-06"), "2026-11-06");
    expect(groups).toEqual([{ key: "week", label: "이번 주", rows: [{ date: "2026-11-06" }] }]);
  });
});

describe("topicParts와 partsText", () => {
  const section: Section = {
    key: "open",
    title: "",
    note: "",
    collapsed: false,
    cards: [
      card("교류", [line("2026-11-10", "서강대학교"), line("2026-11-11", "고려대학교"), line("2026-11-12", "건국대학교")], { topic: "exchange" }),
      card("채용 가", [line("2026-11-10", "신청")], { topic: "career" }),
      card("채용 나", [line("2026-11-11", "신청")], { topic: "career" }),
      card("특강", [line("2026-11-10", "신청")], { topic: "extracurricular" }),
      card("기타", [line("2026-11-10", "신청")], { topic: "other" }),
    ],
  };
  const parts = topicParts(tasksOf(section.cards, TODAY));

  it("행을 주제별로 세고 많은 것부터 적는다", () => {
    expect(parts).toEqual([
      { label: "학점교류", count: 3 },
      { label: "취업", count: 2 },
      { label: "비교과", count: 1 },
      { label: "그 밖", count: 1 },
    ]);
  });

  it("둘까지 적고 나머지는 그 밖으로 합친다", () => {
    expect(partsText(parts)).toBe("학점교류 3 · 취업 2 · 그 밖 2");
    expect(partsText(parts.slice(0, 2))).toBe("학점교류 3 · 취업 2");
    expect(partsText([{ label: "다른 지역 장학", count: 4 }, { label: "다른 학과", count: 0 }])).toBe("다른 지역 장학 4");
    expect(partsText([])).toBe("");
  });
});

describe("dayText", () => {
  it("이번 달이거나 두 주 안이면 요일과 날짜를 적는다", () => {
    expect(dayText("2026-11-12", "2026-11-06")).toBe("목 12");
    expect(dayText("2026-11-30", "2026-11-06")).toBe("월 30");
    expect(dayText("2026-12-03", "2026-11-25")).toBe("목 3");
  });

  it("더 멀면 월과 날짜를 적는다", () => {
    expect(dayText("2026-12-03", "2026-11-06")).toBe("12.3");
    expect(dayText("2027-02-04", "2026-11-06")).toBe("2.4");
  });
});
