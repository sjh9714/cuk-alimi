import { describe, expect, it } from "vitest";
import type { Card, Line, Section } from "./api";
import { boardCards, cleanTitle, eventsOf, monthCells, schoolName, startsOf, tasksOf } from "./tasks";

const TODAY = "2026-11-02";
let nextId = 1;

function line(endsOn: string, label: string | null, extra: Partial<Line> = {}): Line {
  return { id: nextId++, label, startsOn: null, endsOn, startsAt: null, endsAt: null, kind: "ACTION", saved: false, done: false, ...extra };
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
    expect(cleanTitle("<한국고등교육재단> 2027년 인재림 제7기 장학생 선발 안내")).toBe("인재림 제7기 장학생 선발");
    expect(cleanTitle("[한국장학재단]2026년 2학기 푸른등대 기부장학금(2차) 신청 안내")).toBe("2학기 푸른등대 기부장학금(2차) 신청");
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

describe("monthCells", () => {
  it("2026년 11월은 1일이 일요일이라 앞 빈칸 없이 5주다", () => {
    const cells = monthCells(2026, 10);
    expect(cells).toHaveLength(35);
    expect(cells[0].key).toBe("2026-11-01");
    expect(cells[0].inMonth).toBe(true);
    expect(cells[29].key).toBe("2026-11-30");
    expect(cells[34].key).toBe("2026-12-05");
    expect(cells[34].inMonth).toBe(false);
  });

  it("1일 앞은 지난달 날짜로 채우고 요일 열이 맞는다", () => {
    // 2026년 10월 1일은 목요일이다.
    const cells = monthCells(2026, 9);
    expect(cells[0].key).toBe("2026-09-27");
    expect(cells[4].key).toBe("2026-10-01");
    expect(cells.filter((c) => c.inMonth)).toHaveLength(31);
    expect(cells.every((c, i) => c.date.getDay() === i % 7)).toBe(true);
  });

  it("2027년 2월처럼 6주가 필요하지 않은 달도 7의 배수다", () => {
    const cells = monthCells(2027, 1);
    expect(cells.length % 7).toBe(0);
    expect(cells.filter((c) => c.inMonth)).toHaveLength(28);
  });
});
