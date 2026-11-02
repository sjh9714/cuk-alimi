"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { api, dday, type Card, type Feed, type Line, type Me, type Section } from "@/lib/api";
import { MonthCalendar } from "./calendar";
import { CheckIcon, ChevronIcon, ExternalIcon } from "./icons";

type Kind = Card["kind"];
const KIND_LABEL: Record<Kind, string> = {
  schedule: "학사일정",
  scholarship: "장학",
  academic: "학사",
  career: "취업",
};
const WEEKDAY = ["일", "월", "화", "수", "목", "금", "토"];

/** 같은 날 마감인 카드 묶음. date가 null이면 날짜 확인 중인 카드들이다. */
type DayGroup = { date: string | null; cards: Card[] };

export default function Home() {
  const [feed, setFeed] = useState<Feed | null>(null);
  const [me, setMe] = useState<Me | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [kind, setKind] = useState<Kind | "all">("all");
  const [month, setMonth] = useState(() => new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  const [day, setDay] = useState<string | null>(null);

  const load = () =>
    Promise.all([api.feed(), api.me()])
      .then(([f, m]) => {
        setFeed(f);
        setMe(m);
        setError(null);
      })
      .catch((e) => setError(e.message));

  useEffect(() => {
    load();
  }, []);

  const toggleSave = async (line: Line) => {
    try {
      if (line.saved) await api.unsave(line.id);
      else await api.save(line.id);
      setFeed(await api.feed());
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const sections = useMemo(() => feed?.sections ?? [], [feed]);
  const allCards = useMemo(() => sections.flatMap((s) => s.cards), [sections]);
  const openCards = useMemo(() => sections.filter((s) => !s.collapsed).flatMap((s) => s.cards), [sections]);
  const counts = useMemo(() => {
    const c: Record<Kind, number> = {
      schedule: 0,
      scholarship: 0,
      academic: 0,
      career: 0,
    };
    allCards.forEach((card) => c[card.kind]++);
    return c;
  }, [allCards]);
  const marked = useMemo(
    () =>
      new Set(
        allCards.flatMap((c) =>
          lines(c)
            .filter((l) => l.kind === "ACTION")
            .map((l) => l.endsOn),
        ),
      ),
    [allCards],
  );
  const saved = useMemo(
    () =>
      allCards
        .flatMap((c) =>
          lines(c)
            .filter((l) => l.saved)
            .map((l) => ({ card: c, line: l })),
        )
        .sort((a, b) => a.line.endsOn.localeCompare(b.line.endsOn)),
    [allCards],
  );

  const filtering = kind !== "all" || day !== null;
  const keep = (c: Card) =>
    (kind === "all" || c.kind === kind) && (!day || lines(c).some((l) => l.kind === "ACTION" && l.endsOn === day));
  const clearFilters = () => {
    setKind("all");
    setDay(null);
  };

  // 열린 구간은 날짜 한 줄기로 합치고, 접힌 구간은 아래에 둔다 (ADR 012).
  const open = groupByDay(openCards.filter(keep), day);
  const folded = sections
    .filter((s) => s.collapsed)
    .map((s) => ({
      section: s,
      groups: groupByDay(s.cards.filter(keep), day),
    }));
  // 머리글은 필터와 상관없이 가장 가까운 마감을 말한다.
  const nearest = groupByDay(openCards, null).find((g) => g.date);
  const today = new Date();

  return (
    <main id="main">
      <header className="hero">
        <p className="today">
          {today.getMonth() + 1}월 {today.getDate()}일 {WEEKDAY[today.getDay()]}
          요일
        </p>
        <h1>
          {feed === null
            ? "다가오는 마감"
            : nearest?.date
              ? headline(dday(nearest.date), nearest.cards.length)
              : "남은 마감이 없어요"}
        </h1>
      </header>

      {feed === null ? (
        error ? (
          <p className="error-line" role="alert">
            {error}
            <button className="text" onClick={load}>
              다시 시도
            </button>
          </p>
        ) : (
          <div className="loading" aria-label="불러오는 중">
            <div className="bar" />
            <div className="bar" />
            <div className="bar" />
          </div>
        )
      ) : (
        <div className="home">
          <div className="feed">
            {error && (
              <p className="error-line" role="alert">
                {error}
              </p>
            )}
            <div className="toolbar">
              <div className="segmented" role="group" aria-label="종류">
                <button aria-pressed={kind === "all"} onClick={() => setKind("all")}>
                  전체<span className="n">{allCards.length}</span>
                </button>
                {(Object.keys(KIND_LABEL) as Kind[])
                  .filter((k) => k !== "career" || counts.career > 0)
                  .map((k) => (
                    <button key={k} aria-pressed={kind === k} onClick={() => setKind(k)}>
                      {KIND_LABEL[k]}
                      <span className="n">{counts[k]}</span>
                    </button>
                  ))}
              </div>
              {day && (
                <p className="filtering">
                  {shortDate(day)} 마감만 보는 중{" "}
                  <button className="text" onClick={clearFilters}>
                    전체 보기
                  </button>
                </p>
              )}
            </div>

            {open.length === 0 && (
              <p className="empty">
                {filtering ? (
                  <>
                    조건에 맞는 마감이 없어요.{" "}
                    <button className="text" onClick={clearFilters}>
                      필터 지우기
                    </button>
                  </>
                ) : (
                  "지금 남은 마감이 없어요."
                )}
              </p>
            )}

            <Groups groups={open} canSave={!!me} onToggle={toggleSave} />

            {folded.some((f) => f.groups.length > 0) && (
              <div className="folds">
                {folded
                  .filter((f) => f.groups.length > 0)
                  .map(({ section, groups }) => (
                    <Fold
                      key={section.key}
                      section={section}
                      groups={groups}
                      open={filtering}
                      canSave={!!me}
                      onToggle={toggleSave}
                    />
                  ))}
              </div>
            )}
          </div>

          <aside className="rail" aria-label="달력과 내 저장">
            <div className="calendar">
              <MonthCalendar month={month} onMonth={setMonth} marked={marked} selected={day} onSelect={setDay} />
            </div>
            <section aria-labelledby="saved-title">
              <h2 id="saved-title">
                내 저장 {me && saved.length > 0 && <span className="count">{saved.length}</span>}
              </h2>
              {!me ? (
                <p className="note">
                  <Link href="/settings/">로그인</Link>하면 마감을 저장해 두고 여기서 볼 수 있어요.
                </p>
              ) : saved.length === 0 ? (
                <p className="note">저장한 마감이 없어요. 행 앞의 네모를 눌러 보세요.</p>
              ) : (
                <ul className="side-list">
                  {saved.slice(0, 8).map(({ card, line }) => (
                    <li key={line.id}>
                      <span className="name">{line.label ? `${card.title} ${line.label}` : card.title}</span>
                      <span className={`left ${dday(line.endsOn) <= 3 ? "hot" : ""}`}>
                        {leftText(dday(line.endsOn))}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </aside>
        </div>
      )}
    </main>
  );
}

/** 카드의 날짜 줄 전부. 날짜 확인 중인 카드는 비어 있다. */
function lines(c: Card): Line[] {
  return c.deadline ? [c.deadline, ...c.next] : c.next;
}

/**
 * 대표 마감일로 묶어 날짜순. 같은 날은 내게 맞는 공지, 마감 시각, 제목 순. 날짜 없는 카드는 맨 뒤 한 묶음.
 * 달력에서 날짜를 골랐으면 그날 할 일이 있는 카드를 그날 아래에 모은다.
 */
function groupByDay(cards: Card[], day: string | null): DayGroup[] {
  const byDate = new Map<string, Card[]>();
  const undated: Card[] = [];
  for (const c of cards) {
    if (!c.deadline) undated.push(c);
    else {
      const date = day && lines(c).some((l) => l.kind === "ACTION" && l.endsOn === day) ? day : c.deadline.endsOn;
      byDate.set(date, [...(byDate.get(date) ?? []), c]);
    }
  }
  const groups: DayGroup[] = [...byDate.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, list]) => ({
      date,
      cards: list.sort(
        (a, b) =>
          Number(b.mine) - Number(a.mine) ||
          (a.deadline!.endsAt ?? "99").localeCompare(b.deadline!.endsAt ?? "99") ||
          a.title.localeCompare(b.title, "ko"),
      ),
    }));
  if (undated.length > 0) groups.push({ date: null, cards: undated });
  return groups;
}

function headline(d: number, n: number): string {
  if (d < 0) return "지난 마감이 남아 있어요";
  if (d === 0) return `오늘 마감 ${n}건`;
  if (d === 1) return `내일 마감 ${n}건`;
  return `다음 마감까지 ${d}일`;
}

function leftText(d: number): string {
  return d < 0 ? "지남" : d === 0 ? "오늘" : d === 1 ? "내일" : `${d}일 남음`;
}

function shortDate(isoDate: string): string {
  const [, m, d] = isoDate.split("-");
  return `${Number(m)}월 ${Number(d)}일`;
}

function Groups({ groups, canSave, onToggle }: { groups: DayGroup[]; canSave: boolean; onToggle: (l: Line) => void }) {
  let lastMonth = -1;
  const thisYear = new Date().getFullYear();
  return (
    <>
      {groups.map((g, i) => {
        if (!g.date) {
          return (
            <section key="undated" className="dayg" aria-label="날짜 확인 중">
              <div className="gutter" aria-hidden />
              <div>
                <div className="rule" />
                <ul className="rows">
                  {g.cards.map((c) => (
                    <Row key={c.key} card={c} canSave={false} onToggle={onToggle} />
                  ))}
                </ul>
              </div>
            </section>
          );
        }
        const date = new Date(g.date + "T00:00:00");
        const d = dday(g.date);
        const showMonth = date.getMonth() !== lastMonth;
        lastMonth = date.getMonth();
        const label = `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일 ${WEEKDAY[date.getDay()]}요일, ${leftText(d)}`;
        return (
          <section
            key={g.date}
            className={`dayg ${d <= 3 ? "hot" : ""} ${showMonth && i > 0 ? "newmonth" : ""}`}
            aria-label={label}
          >
            <div className="gutter" aria-hidden>
              {showMonth && (
                <span className="mon">
                  {date.getFullYear() !== thisYear && `${date.getFullYear()}년 `}
                  {date.getMonth() + 1}월
                </span>
              )}
              <span className="num">{date.getDate()}</span>
              <span className="wd">{WEEKDAY[date.getDay()]}요일</span>
              <span className="left">{leftText(d)}</span>
            </div>
            <div>
              <div className="rule" />
              <ul className="rows">
                {g.cards.map((c) => (
                  <Row key={c.key} card={c} canSave={canSave} onToggle={onToggle} />
                ))}
              </ul>
            </div>
          </section>
        );
      })}
    </>
  );
}

function Fold({
  section,
  groups,
  open,
  canSave,
  onToggle,
}: {
  section: Section;
  groups: DayGroup[];
  open: boolean;
  canSave: boolean;
  onToggle: (l: Line) => void;
}) {
  const n = groups.reduce((s, g) => s + g.cards.length, 0);
  return (
    <details className="fold" open={open}>
      <summary>
        <span className="fold-head">
          <span className="fold-title">
            {section.title} <span className="count">{n}</span>
          </span>{" "}
          <span className="note">{section.note}</span>
        </span>
        <ChevronIcon />
      </summary>
      <div className="fold-body">
        <Groups groups={groups} canSave={canSave} onToggle={onToggle} />
      </div>
    </details>
  );
}

function Row({ card, canSave, onToggle }: { card: Card; canSave: boolean; onToggle: (l: Line) => void }) {
  const line = card.deadline;
  const audience = card.audience.scope === "GENERAL" ? null : card.audience.label;
  const meta = [
    ...(audience ? pieces(audience) : []),
    ...(card.audience.grade ? [card.audience.grade] : []),
    ...(line ? when(line) : []),
  ];
  const extras = card.audience.excerpt || card.next.length > 0;
  return (
    <li className={`item ${canSave && line ? "" : "plain"}`}>
      {canSave && line && (
        <button
          className="mark"
          aria-pressed={line.saved}
          aria-label={line.saved ? "저장 해제" : "저장"}
          onClick={() => onToggle(line)}
        >
          {line.saved && <CheckIcon size={14} />}
        </button>
      )}
      <div>
        <h3 className="t">
          <a href={card.url} target="_blank" rel="noreferrer" title={card.sourceTitle}>
            {card.title}
            <span className="ext">
              <ExternalIcon size={13} />
              <span className="sr-only">(학교 {card.kind === "schedule" ? "학사일정" : "공지"} 새 창)</span>
            </span>
          </a>
        </h3>
        <div className="m">
          <span className="kind">{KIND_LABEL[card.kind]}</span>
          {meta.map((t, i) => (
            <span key={i}>{t}</span>
          ))}
          {card.mine && <span className="mine">내게 맞음</span>}
          {extras && (
            <span className="extras">
              {card.audience.excerpt && (
                <details className="more">
                  <summary>
                    자격 보기 <ChevronIcon size={12} />
                  </summary>
                  <p className="excerpt">{card.audience.excerpt}</p>
                </details>
              )}
              {card.next.length > 0 && (
                <details className="more">
                  <summary>
                    이후 일정 {card.next.length} <ChevronIcon size={12} />
                  </summary>
                  <ul>
                    {card.next.map((l) => (
                      <li key={l.id} className={l.kind === "INFO" ? "info" : ""}>
                        <span className="d">{numDate(l.endsOn)}</span>
                        <span>
                          {l.label ?? "마감"}
                          {l.kind === "INFO" && " (안내)"}
                        </span>
                      </li>
                    ))}
                  </ul>
                </details>
              )}
            </span>
          )}
        </div>
      </div>
    </li>
  );
}

/** 서버가 가운뎃점으로 이어 준 라벨을 조각으로 나눈다. 화면에서는 간격으로만 띄운다. */
function pieces(label: string): string[] {
  return label
    .split(/\s*[·•]\s*/)
    .map((s) => s.trim())
    .filter(Boolean);
}

/** 날짜는 홈통에 있으니 행에는 이름, 시작일, 시각만 적는다. */
function when(line: Line): string[] {
  const parts: string[] = [];
  if (line.label) parts.push(...pieces(line.label));
  if (line.startsOn && line.startsOn !== line.endsOn) parts.push(`${numDate(line.startsOn)}부터`);
  if (line.endsAt) parts.push(`${line.endsAt.slice(0, 5)}까지`);
  return parts;
}

function numDate(isoDate: string): string {
  const [, m, d] = isoDate.split("-");
  return `${Number(m)}.${Number(d)}`;
}
