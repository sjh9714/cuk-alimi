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
  /** 저장 대상. 한 줄로 합친 경우 첫 줄이다. */
  line: Line;
  saved: boolean;
  /** 서버에 남은 끝냈어요. 로그인 전에는 false이고 브라우저 저장소가 대신한다. */
  done: boolean;
};

const EMPTY_LABEL = /^(마감 기한|마감|기간|신청기간|접수기간|모집기간|조사기간|내용|일정)$/;
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
  return t
    .replace(/^\d{4}학년도\s*/, "")
    .replace(/^\d{4}년도?\s*/, "")
    .replace(/^\d{4}-/, "")
    .replace(/\s*\([^)]*\)?\s*$/, "")
    .replace(/\s*안내\s*$/, "")
    .trim();
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
      tasks.push({
        key: `${card.key}:${date}`,
        date,
        title: titles.join(", "),
        from: [named[0].name.from, since(line, today)].filter(Boolean).join(", "),
        at: at ? `${at.slice(0, 5)}까지` : "",
        kind: card.kind,
        mine: card.mine,
        mineReason: card.mineReason ?? null,
        url: card.url,
        sourceTitle: card.sourceTitle,
        excerpt: card.audience.excerpt,
        line,
        saved: group.some((l) => l.saved),
        done: group.some((l) => l.done),
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
