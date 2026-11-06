// 피드의 공지 카드를 학생이 할 일 한 줄로 바꾼다.
// 화면에는 공문 제목 대신 "서강대 학점교류 신청"처럼 할 일을 적는다. 날짜는 서버 값을 그대로 쓰고 추측하지 않는다.

import type { Card, Line, Section } from "./api";

export type Kind = Card["kind"];

export type Task = {
  key: string;
  date: string;
  title: string;
  from: string;
  at: string;
  kind: Kind;
  mine: boolean;
  mineReason: string | null;
  url: string;
  sourceTitle: string;
  excerpt: string | null;
  /** 대표 줄. 한 줄로 합친 경우 첫 줄이다. */
  line: Line;
  /** 이 행에 합친 줄 전부. 체크와 저장은 모두에 보낸다. 하나만 보내면 나머지 줄의 알림과 캘린더가 계속 나간다. */
  lines: Line[];
  saved: boolean;
  /** 서버에 남은 끝냈어요. 로그인 전에는 false이고 브라우저 저장소가 대신한다. */
  done: boolean;
  /** 그림에서 읽은 날짜가 섞인 행. 확인 표시를 단다. */
  fromImage: boolean;
};

const EMPTY_LABEL = /^(마감 기한|마감|기간|신청기간|접수기간|모집기간|조사기간|내용|일정)$/;

/**
 * 무엇을 하는지만 있고 무엇의 일인지가 없는 라벨의 낱말. "트리니티 신청", "원본서류 제출", "신청서 접수"는 이 낱말만으로 되어 있어서
 * 행 제목만 훑으면 어느 장학·프로그램인지 알 수 없다. 이런 라벨은 공지에서 대상 이름을 앞에 붙인다.
 */
const BARE_WORDS = new Set([
  "트리니티", "온라인", "오프라인", "방문", "이메일", "우편", "원본", "원본서류", "서류", "제출서류", "구비서류", "신청서", "지원서", "참가신청서",
  "신청", "접수", "제출", "등록", "및", "신청서류", "최종", "1차", "2차", "추가", "온라인신청",
]);

/** 공지 제목에서 대상 이름. "하나금융나눔재단 하나장학생 선발"이면 "하나장학생"이다. 못 찾으면 null. */
export function subjectOf(cleanedTitle: string): string | null {
  const words = cleanedTitle.replace(/\([^)]*\)/g, " ").split(/\s+/).filter(Boolean);
  const at = words.map((w, i) => ({ w, i })).reverse()
    .find(({ w }) => /(장학생|장학금|장학|인턴|프로그램|교류|대회|캠프|공모전|해커톤|아카데미|전형|과정|멘토단?|봉사단|서포터즈|챌린지)$/.test(w));
  if (at) {
    // "장학생", "아카데미"처럼 낱말 하나로는 어느 것인지 모르면 앞의 이름을 붙인다("두산연강재단 장학생").
    if (/^(장학생|장학금|장학|인턴|프로그램|아카데미|과정|전형|캠프|대회)$/.test(at.w)) {
      const before = words.slice(0, at.i).reverse().find((w) => !/^(대학|대학생|신규|우수|\d+학기|하반기|상반기|\d{4}년도?)$/.test(w));
      if (before) return `${before} ${at.w}`;
    }
    return at.w;
  }
  const rest = words.filter((w) => !/^(\d+학기|하반기|상반기|\d+기|\d{4}학년도|\d{4}년)$/.test(w) && !/^(선발|모집|신청|접수|안내|공고)$/.test(w));
  return rest.length > 0 ? rest.slice(0, 2).join(" ") : null;
}

function bareLabel(pieces: string[]): boolean {
  const words = pieces.join(" ").split(/[\s·,/]+/).filter(Boolean);
  return words.length > 0 && words.every((w) => BARE_WORDS.has(w));
}
const RECOMMEND_TAG = /^\[대플-추천채용\]\s*/;

export function iso(d: Date): string {
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

/** 공문 제목에서 머리말, 연도, 끝의 "안내"를 덜어낸다. */
export function cleanTitle(title: string): string {
  let t = title.trim();
  let before = "";
  while (t !== before) {
    before = t;
    t = t.replace(/^(\[[^\]]*\]|\([^)]*\)|<[^>]*>)\s*/, "");
  }
  t = t
    .replace(/^\d{4}학년도\s*/, "")
    .replace(/^\d{4}년도?\s*/, "")
    .replace(/^\d{4}-/, "")
    .replace(/\s*\([^)]*\)?\s*$/, "")
    // 행 제목이 두 줄을 넘지 않게 뜻 없는 부분을 더 덜어낸다. 낫표, 가운데 연도, 숫자 없는 가운데 괄호("(GGGI)", "(공공행정 인턴형)").
    // "(2차)"처럼 숫자가 든 괄호는 뜻이 있어 남긴다.
    .replace(/[「」『』]/g, "")
    .replace(/(^|\s)\d{4}년\s+/g, "$1")
    .replace(/\s*\([^)\d]{1,20}\)/g, "");
  // 끝의 "안내", "공고", "알림", "운영", "실시"는 겹쳐 붙기도 한다("모집 공고 안내").
  let prev = "";
  while (t !== prev) {
    prev = t;
    t = t.replace(/\s*(안내|공고|알림|운영|실시)\s*$/, "").trim();
  }
  return t.replace(/\s{2,}/g, " ").trim();
}

/** "한양대학교 (안산ERICA) · 경인"을 "한양대 ERICA"로 줄인다. 학교 이름이 아니면 null. */
export function schoolName(text: string): string | null {
  const m = text.match(/([가-힣]+?)대학교(?:\s*\(([^)]*)\))?/);
  if (!m) return null;
  const name = `${m[1].replace(/여자$/, "여")}대`;
  const campus = m[2]?.match(/[A-Za-z]+/)?.[0];
  return campus ? `${name} ${campus}` : name;
}

function companyOf(title: string): string {
  return title
    .replace(RECOMMEND_TAG, "")
    .split(/\s[-–]\s/)[0]
    .replace(/\(주\)|주식회사/g, "")
    .trim();
}

type Named = { title: string; from: string; school: string | null };

/** 날짜 줄 하나의 할 일 이름. 라벨이 뜻이 없으면 공문 제목을 줄여 쓴다. */
function nameOf(card: Card, line: Line): Named {
  const clean = cleanTitle(card.title);
  const label = line.label ?? "";
  const exchange = clean.includes("학점교류");

  if (RECOMMEND_TAG.test(card.title) || /추천(자|마감)/.test(label)) {
    return {
      title: `${companyOf(card.title)} 추천 신청`,
      from: RECOMMEND_TAG.test(card.title) ? "대플 추천채용" : "추천채용",
      school: null,
    };
  }
  const school = exchange && label.includes("대학교") ? schoolName(label) : null;
  if (school) return { title: "", from: clean.replace(/\s*대학교\s*학점교류$/, ""), school };

  const pieces = label
    .split(/\s*·\s*/)
    .map((p) => p.trim())
    .filter((p) => p && !EMPTY_LABEL.test(p));
  if (pieces.length === 0) {
    const audience = card.audience.scope === "GENERAL" ? "" : (card.audience.label ?? "");
    return { title: clean, from: audience, school: null };
  }
  const named = schoolName(clean);
  if (exchange && named) return { title: `${named} 학점교류 ${pieces.join(" ")}`, from: "", school: null };
  // "트리니티 신청"처럼 무엇의 일인지 빠진 라벨이면 대상 이름을 앞에 붙인다("하나장학생 트리니티 신청").
  const subject = bareLabel(pieces) ? subjectOf(clean) : null;
  if (subject) return { title: `${subject} ${pieces.join(" ")}`, from: clean, school: null };
  return { title: pieces.join(" "), from: clean, school: null };
}

function since(line: Line, today: string): string {
  if (!line.startsOn || line.startsOn === line.endsOn || line.startsOn <= today) return "";
  const [, m, d] = line.startsOn.split("-");
  return `${Number(m)}월 ${Number(d)}일부터`;
}

/** 카드의 할 일 줄을 날짜별로 펼친다. 같은 공지의 같은 날 줄은 한 줄로 합친다. */
export function tasksOf(cards: Card[], today: string): Task[] {
  const tasks: Task[] = [];
  for (const card of cards) {
    const lines = (card.deadline ? [card.deadline, ...card.next] : card.next).filter((l) => l.kind === "ACTION");
    const byDate = new Map<string, Line[]>();
    for (const line of lines) byDate.set(line.endsOn, [...(byDate.get(line.endsOn) ?? []), line]);

    for (const [date, group] of byDate) {
      const named = group.map((l) => ({ line: l, name: nameOf(card, l) }));
      const schools = named.flatMap((n) => (n.name.school ? [n.name.school] : []));
      const titles = [...new Set(named.filter((n) => !n.name.school).map((n) => n.name.title))];
      if (schools.length > 0) titles.unshift(`${schools.join(", ")} 학점교류 신청`);
      const line = group[0];
      const at = group.find((l) => l.endsAt)?.endsAt;
      // 같은 날 줄이 셋 넘거나 이어 붙인 이름이 길면(졸업시험 대체요건 표) 공문 제목이 더 읽기 쉽다.
      const joined = titles.join(", ");
      const title = titles.length >= 3 || joined.length > 36 ? cleanTitle(card.title) : joined;
      tasks.push({
        key: `${card.key}:${date}`,
        date,
        title,
        from: [named[0].name.from, since(line, today)].filter(Boolean).join(", "),
        at: at ? `${at.slice(0, 5)}까지` : "",
        kind: card.kind,
        mine: card.mine,
        mineReason: card.mineReason ?? null,
        url: card.url,
        sourceTitle: card.sourceTitle,
        excerpt: card.audience.excerpt,
        line,
        lines: group,
        saved: group.some((l) => l.saved),
        done: group.some((l) => l.done),
        fromImage: group.some((l) => l.fromImage),
      });
    }
  }
  return tasks.sort((a, b) => a.date.localeCompare(b.date) || Number(b.mine) - Number(a.mine) || a.title.localeCompare(b.title, "ko"));
}

export type CampusEvent = { key: string; date: string; title: string; at: string; kind: Kind; url: string; line: Line; tag: "행사" | "시작" };

/** 신청할 것 없이 날짜만 알아 두면 되는 행사 (학위수여식 등). */
export function eventsOf(sections: Section[]): CampusEvent[] {
  return sections
    .filter((s) => s.key === "events")
    .flatMap((s) => s.cards)
    .flatMap((c) => {
      if (!c.deadline) return [];
      const time = c.deadline.startsAt ?? c.deadline.endsAt;
      return [{ key: c.key, date: c.deadline.endsOn, title: cleanTitle(c.title), at: time ? time.slice(0, 5) : "", kind: c.kind, url: c.url, line: c.deadline, tag: "행사" }];
    });
}

/** 학사일정 표 항목의 시작일. 수강신청처럼 시작하는 날이 마감만큼 중요한 일정이다. 지난 시작일은 뺀다. */
export function startsOf(sections: Section[], today: string): CampusEvent[] {
  return boardCards(sections)
    .filter((c) => c.kind === "schedule")
    .flatMap((c) => {
      const line = c.deadline;
      if (!line?.startsOn || line.startsOn === line.endsOn || line.startsOn < today) return [];
      return [{ key: `start:${c.key}`, date: line.startsOn, title: c.title, at: "", kind: c.kind, url: c.url, line, tag: "시작" as const }];
    });
}

/** 주간 판에 올릴 구간. 지역과 특정 대상은 ADR 009대로 접어 둔다. */
export function boardCards(sections: Section[]): Card[] {
  return sections.filter((s) => !s.collapsed || s.key === "career").flatMap((s) => s.cards);
}

export type MonthCell = { date: Date; key: string; inMonth: boolean };

/** 월 달력의 칸. 일요일에 시작하는 7열로 그 달을 덮고, 1일 앞과 말일 뒤는 이웃 달 날짜로 채운다. month는 0부터 센다. */
export function monthCells(year: number, month: number): MonthCell[] {
  const first = new Date(year, month, 1);
  const lead = first.getDay();
  const length = new Date(year, month + 1, 0).getDate();
  const count = Math.ceil((lead + length) / 7) * 7;
  return Array.from({ length: count }, (_, i) => {
    const date = new Date(year, month, 1 - lead + i);
    return { date, key: iso(date), inMonth: date.getMonth() === month };
  });
}
