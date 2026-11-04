"use client";

// 홈의 월 보기. 일요일에 시작하는 달력 칸에 할 일 제목을 적고, 날짜를 누르면 아래에 그날 할 일을 주간 판과 같은 행으로 펼친다.

import { useMemo, useState } from "react";
import { dday } from "@/lib/api";
import { monthCells, type CampusEvent, type Task } from "@/lib/tasks";
import { ChevronIcon } from "./icons";
import { leftText, Ring, Row, TAG_CLASS, WEEKDAY, type Board } from "./planner";

const LINES_MAX = 3;

export type MonthKey = { year: number; month: number };

export function monthKeyOf(isoDate: string): MonthKey {
  const [y, m] = isoDate.split("-").map(Number);
  return { year: y, month: m - 1 };
}

function cmp(a: MonthKey, b: MonthKey): number {
  return a.year * 12 + a.month - (b.year * 12 + b.month);
}

function shift(k: MonthKey, n: number): MonthKey {
  const d = new Date(k.year, k.month + n, 1);
  return { year: d.getFullYear(), month: d.getMonth() };
}

export function MonthView({
  tasks,
  events,
  range,
  board,
}: {
  tasks: Task[];
  events: CampusEvent[];
  /** 넘길 수 있는 첫 달과 끝 달. */
  range: { first: MonthKey; last: MonthKey };
  board: Board;
}) {
  const { todayIso } = board;
  const thisMonth = monthKeyOf(todayIso);
  const [cursor, setCursor] = useState<MonthKey>(thisMonth);
  const [picked, setPicked] = useState<string | null>(null);

  const cells = useMemo(() => monthCells(cursor.year, cursor.month), [cursor]);
  const byDay = useMemo(() => {
    const m = new Map<string, { tasks: Task[]; events: CampusEvent[] }>();
    const at = (k: string) => m.get(k) ?? (m.set(k, { tasks: [], events: [] }), m.get(k)!);
    tasks.forEach((t) => at(t.date).tasks.push(t));
    events.forEach((e) => at(e.date).events.push(e));
    return m;
  }, [tasks, events]);

  // 처음 고른 날. 이번 달이면 오늘, 아니면 할 일이 있는 첫 날, 없으면 1일.
  const selected =
    picked && cells.some((c) => c.key === picked && c.inMonth)
      ? picked
      : cmp(cursor, thisMonth) === 0
        ? todayIso
        : (cells.find((c) => c.inMonth && (byDay.get(c.key)?.tasks.length ?? 0) > 0) ?? cells.find((c) => c.inMonth)!).key;

  const go = (k: MonthKey) => {
    setCursor(k);
    setPicked(null);
  };
  const canPrev = cmp(cursor, range.first) > 0;
  const canNext = cmp(cursor, range.last) < 0;
  const day = byDay.get(selected) ?? { tasks: [], events: [] };
  const selDate = new Date(selected + "T00:00:00");
  const past = selected < todayIso;
  const d = dday(selected);

  return (
    <section className="month" aria-label="월 달력">
      <div className="month-nav">
        <button type="button" className="icon" aria-label="이전 달" disabled={!canPrev} onClick={() => go(shift(cursor, -1))}>
          <ChevronIcon dir="left" size={18} />
        </button>
        <h2 aria-live="polite">
          {cursor.year}년 {cursor.month + 1}월
        </h2>
        <button type="button" className="icon" aria-label="다음 달" disabled={!canNext} onClick={() => go(shift(cursor, 1))}>
          <ChevronIcon dir="right" size={18} />
        </button>
        {cmp(cursor, thisMonth) !== 0 && (
          <button type="button" className="text" onClick={() => go(thisMonth)}>
            이번 달
          </button>
        )}
      </div>

      <div className="month-grid">
        {WEEKDAY.map((w) => (
          <span key={w} className="mwd" aria-hidden>
            {w}
          </span>
        ))}
        {cells.map((c) => {
          const own = byDay.get(c.key) ?? { tasks: [], events: [] };
          const lines = [
            ...own.tasks.map((t) => ({ key: t.key, title: t.title, cls: `t ${t.kind} ${t.mine ? "picked" : ""} ${board.done.has(t.key) ? "done" : ""}` })),
            ...own.events.map((e) => ({ key: e.key, title: e.title, cls: `e ${TAG_CLASS[e.tag]}` })),
          ];
          const n = own.tasks.length;
          const cls = [
            "mc",
            c.inMonth ? "" : "out",
            c.key === todayIso ? "now" : "",
            c.key < todayIso ? "past" : "",
            c.key === selected ? "sel" : "",
            c.date.getDay() === 0 || c.date.getDay() === 6 ? "weekend" : "",
          ];
          return (
            <button
              key={c.key}
              type="button"
              className={cls.filter(Boolean).join(" ")}
              aria-pressed={c.key === selected}
              aria-label={`${c.date.getMonth() + 1}월 ${c.date.getDate()}일 ${WEEKDAY[c.date.getDay()]}요일${n ? `, 할 일 ${n}건` : ""}`}
              onClick={() => {
                if (!c.inMonth) setCursor({ year: c.date.getFullYear(), month: c.date.getMonth() });
                setPicked(c.key);
              }}
            >
              <span className="mnum">
                {c.date.getDate()}
                {c.key === todayIso && <Ring />}
              </span>
              {lines.length > 0 && (
                <span className="mlines" aria-hidden>
                  {lines.slice(0, LINES_MAX).map((l) => (
                    <span key={l.key} className={l.cls}>
                      <span>{l.title}</span>
                    </span>
                  ))}
                  {lines.length > LINES_MAX && <span className="more">+{lines.length - LINES_MAX}건</span>}
                </span>
              )}
              {lines.length > 0 && (
                <span className="dots" aria-hidden>
                  {lines.slice(0, LINES_MAX).map((l) => (
                    <i key={l.key} className={l.cls} />
                  ))}
                </span>
              )}
            </button>
          );
        })}
      </div>

      <div className={`day-panel ${past ? "past" : ""}`}>
        <div className="dh">
          <span className="num">{selDate.getDate()}</span>
          <span className="wd">
            {selDate.getMonth() + 1}월 {WEEKDAY[selDate.getDay()]}요일
          </span>
          {day.tasks.length > 0 && !past && <span className={`left ${d <= 2 ? "hot" : ""}`}>{leftText(d)}</span>}
        </div>
        {day.tasks.length === 0 && day.events.length === 0 && <p className="hint">이날 끝나는 일은 없어요.</p>}
        {day.tasks.length > 0 && (
          <ul className="items">
            {day.tasks.map((t) => (
              <Row key={t.key} task={t} {...board} />
            ))}
          </ul>
        )}
        {day.events.length > 0 && (
          <ul className="events">
            {day.events.map((e) => (
              <li key={e.key}>
                <b className={TAG_CLASS[e.tag]}>{e.tag}</b>
                <a href={e.url} target="_blank" rel="noreferrer">
                  {e.title}
                </a>
                {e.at && <span>{e.at}</span>}
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}

/** 불러온 할 일과 행사가 걸친 달. 이번 달은 항상 넣는다. */
export function monthRange(dates: string[], todayIso: string): { first: MonthKey; last: MonthKey } {
  const all = [...dates, todayIso].sort();
  return { first: monthKeyOf(all[0]), last: monthKeyOf(all[all.length - 1]) };
}
