// 피드의 공지 카드를 학생이 할 일 한 줄로 바꾼다.
// 화면에는 공문 제목 대신 "서강대 학점교류 신청"처럼 할 일을 적는다. 날짜는 서버 값을 그대로 쓰고 추측하지 않는다.

import { TOPICS, type Card, type Line, type Section, type TopicKey } from "./api";
import { needsCheck } from "./evidence";

export type Kind = Card["kind"];

export type Task = {
  key: string;
  date: string;
  title: string;
  from: string;
  at: string;
  kind: Kind;
  /** 공지 주제. 행사와 날짜 확인 중 카드에는 없다. */
  topic: TopicKey | "other" | null;
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
  /** 사람 확인 없이 공개한 날짜(그림, AI)나 AI가 의심한 날짜가 섞인 행. "확인"을 달고 신고를 받는다 (ADR 016). */
  check: boolean;
  /** 자격 (ADR 016). "mismatch"는 학년·학기·학과·지역이 프로필과 어긋난 것이다. 로그인 전에는 null이다. */
  eligibility: NonNullable<Card["eligibility"]> | null;
  /** 공지에 적힌 대상 조건 문장. 근거 패널에 함께 보인다. */
  eligibilityEvidence: string | null;
};

/** "신청기간", "모집기한", "마감 기한"처럼 일의 이름이 아니라 자리 이름인 라벨 조각. 서버의 DateItemExtractor.GENERIC_LABEL과 같은 규칙이다. */
const EMPTY_LABEL = /^((신청|접수|모집|조사|확인|평가|이벤트|행사|제출|운영|선발|지원)\s*)?(기간|기한|마감(\s*기한)?|일정|내용|일자)$/;

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
    // 꺾쇠 안이 재단·장학회 이름이면 벗기기만 한다("<아산사회복지재단> …"). 그 이름이 무엇의 일인지 알려 준다.
    t = t.replace(/^<([^>]*(재단|장학회|협회|공단|기금))>\s*/, "$1 ").replace(/^(\[[^\]]*\]|\([^)]*\)|<[^>]*>)\s*/, "");
  }
  t = t
    .replace(/^\d{4}학년도\s*/, "")
    .replace(/^\d{4}년도?\s*/, "")
    .replace(/^\d{4}-/, "")
    .replace(/\s*\([^)]*\)?\s*$/, "")
    // 행 제목이 두 줄을 넘지 않게 뜻 없는 부분을 더 덜어낸다. 낫표, 가운데 연도, 숫자 없는 가운데 괄호("(GGGI)", "(공공행정 인턴형)").
    // "(2차)"처럼 숫자가 든 괄호는 뜻이 있어 남긴다.
    .replace(/[「」『』]/g, "")
    .replace(/(^|\s)\d{4}년도?\s+/g, "$1")
    // 가운데 괄호를 뗄 때 앞뒤 낱말이 붙지 않게 공백을 남긴다("하반기 (재)논산시장학회"가 "하반기논산시장학회"가 되던 문제).
    .replace(/\s*\([^)\d]{1,20}\)\s*/g, " ");
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

/** 추천채용 공지 제목의 회사 이름. "㈜삼원페이퍼-영업 사원 모집"은 "삼원페이퍼", "JB주식회사(구 중부도시가스) – …"는 "JB"다. */
function companyOf(title: string): string {
  return title
    .replace(RECOMMEND_TAG, "")
    .split(/\s[-–]\s|[-–](?=[가-힣])/)[0]
    .replace(/㈜|\(주\)|주식회사/g, "")
    .replace(/\([^)]*\)/g, "")
    .replace(/\s{2,}/g, " ")
    .trim();
}

/** 추천채용 줄의 단계. 같은 공지에 자소서와 추천서 마감이 따로 있으면 행만 보고도 구별되게 한다. */
function recommendStep(label: string): string {
  if (/자소서|자기소개서/.test(label)) return "자소서 제출";
  if (/추천서/.test(label)) return "추천서 제출";
  return "추천 신청";
}

type Named = { title: string; from: string; school: string | null; company?: string };

/** 날짜 줄 하나의 할 일 이름. 라벨이 뜻이 없으면 공문 제목을 줄여 쓴다. */
function nameOf(card: Card, line: Line): Named {
  const clean = cleanTitle(card.title);
  const label = line.label ?? "";
  const exchange = clean.includes("학점교류");

  if (RECOMMEND_TAG.test(card.title) || /추천(자|마감)/.test(label)) {
    const company = companyOf(card.title);
    return {
      title: `${company} ${recommendStep(label)}`,
      from: RECOMMEND_TAG.test(card.title) ? "대플 추천채용" : "추천채용",
      school: null,
      company,
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
      const company = named.find((n) => n.name.company)?.name.company;
      // 추천채용은 같은 날 줄이 여럿이면("입사지원서 제출", "추천마감") 한 일이라 "회사 추천 신청" 하나로 쓴다.
      const distinct = [...new Set(named.filter((n) => !n.name.school).map((n) => n.name.title))];
      const titles = company && distinct.length > 1 ? [`${company} 추천 신청`] : distinct;
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
        topic: card.topic ?? null,
        mine: card.mine,
        mineReason: card.mineReason ?? null,
        url: card.url,
        sourceTitle: card.sourceTitle,
        excerpt: card.audience.excerpt,
        line,
        lines: group,
        saved: group.some((l) => l.saved),
        done: group.some((l) => l.done),
        check: group.some(needsCheck),
        eligibility: card.eligibility ?? null,
        eligibilityEvidence: card.eligibilityEvidence ?? null,
      });
    }
  }
  return tasks.sort((a, b) => a.date.localeCompare(b.date) || Number(b.mine) - Number(a.mine) || a.title.localeCompare(b.title, "ko"));
}

export type CampusEvent = { key: string; date: string; title: string; at: string; kind: Kind; url: string; line: Line };

/** 신청할 것 없이 날짜만 알아 두면 되는 행사 (학위수여식 등). */
export function eventsOf(sections: Section[]): CampusEvent[] {
  return sections
    .filter((s) => s.key === "events")
    .flatMap((s) => s.cards)
    .flatMap((c) => {
      if (!c.deadline) return [];
      const time = c.deadline.startsAt ?? c.deadline.endsAt;
      return [{ key: c.key, date: c.deadline.endsOn, title: cleanTitle(c.title), at: time ? time.slice(0, 5) : "", kind: c.kind, url: c.url, line: c.deadline }];
    });
}

/** 목록에 올릴 수 있는 구간. 열린 구간과 취업이다. 지역과 특정 대상은 ADR 009대로 접어 둔다. */
export function boardCards(sections: Section[]): Card[] {
  return sections.filter((s) => !s.collapsed || s.key === "career").flatMap((s) => s.cards);
}

export const WEEKDAY = ["일", "월", "화", "수", "목", "금", "토"];

export function addDays(d: Date, n: number): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
}

/** 그 날이 든 주의 월요일. */
export function mondayOf(d: Date): Date {
  return addDays(d, -((d.getDay() + 6) % 7));
}

function dateOf(isoDate: string): Date {
  const [y, m, d] = isoDate.split("-").map(Number);
  return new Date(y, m - 1, d);
}

/**
 * 판에 오르는 행을 목록에 둘 것, "관심 밖"으로 접을 것, "다른 대상"으로 접을 것으로 나눈다 (ADR 015, 016).
 * 학사일정 표 항목(must 구간)과 저장한 행은 어느 쪽으로도 접지 않는다.
 * 프로필과 어긋난 행(mismatch)은 관심 주제보다 먼저 본다. 내 일이 아닌 것이 관심 밖보다 분명한 이유다.
 * 주제가 기타이거나 없는 행은 관심 주제와 상관없이 목록에 남는다. 기타는 학생이 고를 수 없는 주제라 접으면 되돌릴 길이 없다.
 */
export function splitByInterest(
  sections: Section[],
  interests: readonly string[],
  today: string,
): { main: Task[]; outside: Task[]; mismatch: Task[] } {
  const must = new Set(tasksOf(sections.filter((s) => s.key === "must").flatMap((s) => s.cards), today).map((t) => t.key));
  const pinned = (t: Task) => must.has(t.key) || t.saved;
  const off = (t: Task) => !pinned(t) && t.eligibility === "mismatch";
  const stays = (t: Task) => pinned(t) || !t.topic || t.topic === "other" || interests.includes(t.topic);
  const all = tasksOf(boardCards(sections), today);
  const rest = all.filter((t) => !off(t));
  return { main: rest.filter(stays), outside: rest.filter((t) => !stays(t)), mismatch: all.filter(off) };
}

export type When = "week" | "next" | "month" | "later";
const WHEN_LABEL: Record<When, string> = { week: "이번 주", next: "다음 주", month: "이달 안", later: "그 뒤" };

/**
 * 날짜순 행을 이번 주, 다음 주, 이달 안, 그 뒤로 묶는다. 주는 월요일에 시작한다.
 * 지난 날의 행은 버리고, 빈 묶음은 만들지 않는다. 다음 주가 달을 넘기면 이달 안은 없다.
 */
export function groupByWhen<T extends { date: string }>(rows: T[], today: string): { key: When; label: string; rows: T[] }[] {
  const now = dateOf(today);
  const monday = mondayOf(now);
  const next = iso(addDays(monday, 7));
  const after = iso(addDays(monday, 14));
  const monthEnd = iso(new Date(now.getFullYear(), now.getMonth() + 1, 0));
  const whenOf = (date: string): When => (date < next ? "week" : date < after ? "next" : date <= monthEnd ? "month" : "later");
  const upcoming = rows.filter((r) => r.date >= today);
  return (["week", "next", "month", "later"] as const)
    .map((key) => ({ key, label: WHEN_LABEL[key], rows: upcoming.filter((r) => whenOf(r.date) === key) }))
    .filter((g) => g.rows.length > 0);
}

export type Part = { label: string; count: number };

/** 행을 주제별로 센다. 많은 주제가 먼저이고, 주제가 기타이거나 없으면 "그 밖"이다. */
export function topicParts(tasks: Task[]): Part[] {
  const count = new Map<string, number>();
  for (const t of tasks) {
    const label = TOPICS.find((x) => x.key === t.topic)?.short ?? "그 밖";
    count.set(label, (count.get(label) ?? 0) + 1);
  }
  const order = [...TOPICS.map((t) => t.short as string), "그 밖"];
  return [...count].map(([label, n]) => ({ label, count: n })).sort((a, b) => b.count - a.count || order.indexOf(a.label) - order.indexOf(b.label));
}

/** "학점교류 7 · 취업 3"처럼 잇는다. max개를 넘는 나머지는 "그 밖 N"으로 합친다. */
export function partsText(parts: Part[], max = 2): string {
  const shown = parts.filter((p) => p.count > 0);
  if (shown.length <= max) return shown.map((p) => `${p.label} ${p.count}`).join(" · ");
  const rest = shown.slice(max).reduce((n, p) => n + p.count, 0);
  return [...shown.slice(0, max).map((p) => `${p.label} ${p.count}`), `그 밖 ${rest}`].join(" · ");
}

/** 남은 날 아래에 적는 날짜. 이번 달이거나 두 주 안이면 "목 12", 더 멀면 "12.3". */
export function dayText(date: string, today: string): string {
  const d = dateOf(date);
  const now = dateOf(today);
  const near = date < iso(addDays(now, 14)) || (d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth());
  return near ? `${WEEKDAY[d.getDay()]} ${d.getDate()}` : `${d.getMonth() + 1}.${d.getDate()}`;
}
