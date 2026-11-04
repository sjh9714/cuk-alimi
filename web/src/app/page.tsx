"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { api, dday, type Feed, type Line, type Me } from "@/lib/api";
import { boardCards, cleanTitle, eventsOf, iso, startsOf, tasksOf, type CampusEvent, type Kind, type Task } from "@/lib/tasks";
import { BookmarkIcon, CheckIcon, ChevronIcon } from "./icons";

/** 학사일정 표와 학사 공지는 출처만 다르다. 학생에게는 둘 다 학사다. */
const KIND_LABEL: Record<Kind, string> = { schedule: "학사", scholarship: "장학", academic: "학사", career: "취업", dept: "학과" };
const KINDS: (Kind | "all")[] = ["all", "scholarship", "academic", "career", "dept"];
const WEEKDAY = ["일", "월", "화", "수", "목", "금", "토"];
const TAG_CLASS = { 시작: "start", 마감: "end", 행사: "event" };
const DONE_KEY = "cuk-alimi.done";
const MEMO_MAX = 6;

/** 정적 HTML에는 빌드한 날이 박힌다. 이번 주는 브라우저에서 처음 그릴 때 정한다. */
export default function Home() {
  const [today, setToday] = useState<Date | null>(null);
  useEffect(() => setToday(new Date()), []);
  if (!today) {
    return (
      <main id="main" className="planner">
        <div className="spread loading" aria-label="불러오는 중" />
      </main>
    );
  }
  return <Planner today={today} />;
}

function Planner({ today }: { today: Date }) {
  const [feed, setFeed] = useState<Feed | null>(null);
  const [me, setMe] = useState<Me | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [kind, setKind] = useState<Kind | "all">("all");
  const [done, setDone] = useState<Set<string>>(new Set());
  const todayIso = iso(today);
  const tasksAll = useMemo(() => tasksOf(boardCards(feed?.sections ?? []), todayIso), [feed, todayIso]);

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
    setDone(readDone());
  }, []);

  // 로그인했으면 끝냈어요는 서버 값이다. 이 브라우저에만 있던 체크는 처음 한 번 서버로 옮기고 지운다.
  const serverDone = useMemo(() => (me ? new Set(tasksAll.filter((t) => t.done).map((t) => t.key)) : null), [me, tasksAll]);
  useEffect(() => {
    if (!me || !feed) return;
    const local = readDone();
    const pending = tasksAll.filter((t) => local.has(t.key) && !t.done);
    if (pending.length === 0) {
      if (local.size > 0) writeDone(new Set());
      return;
    }
    Promise.allSettled(pending.map((t) => api.markDone(t.line.id)))
      .then(() => api.feed())
      .then((f) => {
        writeDone(new Set());
        setFeed(f);
      })
      .catch(() => {});
  }, [me, feed]);

  const toggleSave = async (line: Line) => {
    setError(null);
    try {
      if (line.saved) await api.unsave(line.id);
      else await api.save(line.id);
      setFeed(await api.feed());
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const toggleDone = async (key: string) => {
    const task = tasksAll.find((t) => t.key === key);
    if (me && task) {
      setError(null);
      try {
        if (task.done) await api.unmarkDone(task.line.id);
        else await api.markDone(task.line.id);
        setFeed(await api.feed());
      } catch (e) {
        setError((e as Error).message);
      }
      return;
    }
    const next = new Set(done);
    if (!next.delete(key)) next.add(key);
    setDone(next);
    writeDone(next);
  };

  const monday = mondayOf(today);
  const sections = useMemo(() => feed?.sections ?? [], [feed]);
  const tasks = tasksAll;
  const inKind = (k: Kind) => kind === "all" || k === kind || (kind === "academic" && k === "schedule");
  const shown = tasks.filter((t) => inKind(t.kind));
  const events = [...eventsOf(sections), ...startsOf(sections, todayIso)].filter((e) => inKind(e.kind));
  const week2 = iso(addDays(monday, 7));
  const after = iso(addDays(monday, 14));
  const finishedKeys = serverDone ?? done;
  const open = (t: Task) => !finishedKeys.has(t.key);
  const word = kind === "all" ? "일" : `${KIND_LABEL[kind]} 일정`;
  const todayCount = shown.filter((t) => t.date === todayIso && open(t)).length;
  const weekCount = shown.filter((t) => t.date >= todayIso && t.date < week2 && open(t)).length;
  const upcoming = upcomingRows(shown, events, after);
  const others = shown.filter((t) => t.date >= after && t.kind !== "schedule");
  const undated = (sections.find((s) => s.key === "undated")?.cards ?? []).filter((c) => inKind(c.kind));
  const folded = sections
    .filter((s) => s.key === "region" || s.key === "group" || s.key === "dept")
    .map((s) => ({ ...s, cards: s.cards.filter((c) => inKind(c.kind)) }))
    .filter((s) => s.cards.length > 0);
  const saved = shown.filter((t) => t.saved);

  const board = { todayIso, done: finishedKeys, canSave: !!me, onDone: toggleDone, onSave: toggleSave };

  return (
    <main id="main" className="planner">
      <div className="head">
        <div>
          <h1>{weekTitle(monday)}</h1>
          {feed && (
            <p className={`say ${todayCount ? "" : "calm"}`} aria-live="polite">
              {todayCount
                ? `오늘 끝나는 ${word} ${todayCount}건`
                : weekCount
                  ? `이번 주 남은 ${word} ${weekCount}건`
                  : `이번 주에 끝나는 ${word}은 없어요`}
            </p>
          )}
        </div>
        {feed && (
          <div className="head-tools">
            {me && (
              <p className="legend">
                <span className="swatch" aria-hidden="true" />
                내게 맞음
              </p>
            )}
            <div className="seg" role="group" aria-label="종류">
              {KINDS.map((k) => (
                <button key={k} aria-pressed={kind === k} onClick={() => setKind(k)}>
                  {k === "all" ? "전체" : KIND_LABEL[k]}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>

      {error && (
        <p className="error-line" role="alert">
          {error}
          {!feed && (
            <button className="text" onClick={load}>
              다시 시도
            </button>
          )}
        </p>
      )}

      {!feed && !error && <div className="spread loading" aria-label="불러오는 중" />}

      {feed && (
        <>
          <div className="spread">
            {days(monday).map((d) => (
              <Day key={iso(d)} date={d} tasks={shown} events={events} {...board} />
            ))}
            <div className="cell memo">
              <h2>{undated.length ? `날짜 확인 중 ${undated.length}건` : "날짜 확인 중"}</h2>
              {undated.length === 0 ? (
                <p className="hint">지금은 없어요.</p>
              ) : (
                <>
                  <p className="hint">날짜가 공지 그림에만 있어요.</p>
                  <ul>
                    {undated.slice(0, MEMO_MAX).map((c) => (
                      <li key={c.key}>
                        <a href={c.url} target="_blank" rel="noreferrer">
                          {cleanTitle(c.title)}
                        </a>
                      </li>
                    ))}
                  </ul>
                  {undated.length > MEMO_MAX && <p className="hint">외 {undated.length - MEMO_MAX}건</p>}
                </>
              )}
            </div>
          </div>

          <div className="next">
            <h2>다음 주</h2>
            <span>
              {rangeText(addDays(monday, 7), addDays(monday, 13))}{" "}
              {shown.filter((t) => t.date >= week2 && t.date < after).length}건
            </span>
          </div>
          <div className="spread">
            {days(addDays(monday, 7)).map((d) => (
              <Day key={iso(d)} date={d} tasks={shown} events={events} {...board} />
            ))}
            <div className="cell memo">
              <h2>{me ? `내 저장 ${saved.length}건` : "내 저장"}</h2>
              {!me ? (
                <p className="hint">
                  <Link href="/settings/">로그인</Link>하면 마감을 저장하고 메일로 받을 수 있어요.
                </p>
              ) : saved.length === 0 ? (
                <p className="hint">행 끝의 책갈피를 누르면 여기에 모여요.</p>
              ) : (
                <>
                  <ul>
                    {saved.slice(0, MEMO_MAX).map((t) => (
                      <li key={t.key} className={finishedKeys.has(t.key) || t.date < todayIso ? "done" : undefined}>
                        <a href={t.url} target="_blank" rel="noreferrer">
                          {t.title}
                        </a>
                        <span>{shortDate(t.date)}</span>
                      </li>
                    ))}
                  </ul>
                  {saved.length > MEMO_MAX && <p className="hint">외 {saved.length - MEMO_MAX}건</p>}
                </>
              )}
            </div>
          </div>

          <section className="upcoming" aria-labelledby="upcoming-title">
            <div className="next">
              <h2 id="upcoming-title">앞으로의 일정</h2>
            </div>
            {upcoming.length === 0 ? (
              <p className="hint">앞으로 남은 일정이 없어요.</p>
            ) : (
              <ul className="fold-list">
                {upcoming.map((r) => (
                  <li key={r.key}>
                    <span className="d">{shortDate(r.date)}</span>
                    <span className={`tag ${TAG_CLASS[r.tag]}`}>{r.tag}</span>
                    <a href={r.url} target="_blank" rel="noreferrer">
                      {r.title}
                    </a>
                  </li>
                ))}
              </ul>
            )}
            {others.length > 0 && (
              <details className="fold">
                <summary>
                  <span className="fold-title">
                    다른 공지 일정 <span className="count">{others.length}</span>
                  </span>
                  <ChevronIcon size={15} />
                </summary>
                <div className="fold-body">
                  <FoldList tasks={others} tagged />
                </div>
              </details>
            )}
          </section>

          <div className="folds">
            {folded.map((s) => (
              <Fold key={s.key} title={s.title} count={s.cards.length} note={s.note}>
                <FoldList tasks={tasksOf(s.cards, todayIso)} />
              </Fold>
            ))}
          </div>
        </>
      )}
    </main>
  );
}

type Board = {
  todayIso: string;
  done: Set<string>;
  canSave: boolean;
  onDone: (key: string) => void;
  onSave: (line: Line) => void;
};

function Day({ date, tasks, events, todayIso, ...board }: Board & { date: Date; tasks: Task[]; events: CampusEvent[] }) {
  const key = iso(date);
  const own = tasks.filter((t) => t.date === key);
  const ownEvents = events.filter((e) => e.date === key);
  const d = dday(key);
  const past = key < todayIso;
  const hot = d >= 0 && d <= 2;
  const cls = ["cell", key === todayIso ? "now" : "", past ? "past" : "", hot ? "hot" : "", (own.length === 0 && ownEvents.length === 0) || past ? "quiet" : ""];
  return (
    <section
      className={cls.filter(Boolean).join(" ")}
      aria-label={`${date.getMonth() + 1}월 ${date.getDate()}일 ${WEEKDAY[date.getDay()]}요일`}
    >
      <div className="dh" aria-hidden>
        <span className="num">
          {date.getDate()}
          {key === todayIso && <Ring />}
        </span>
        <span className="wd">{WEEKDAY[date.getDay()]}요일</span>
        {own.length > 0 && !past && <span className="left">{leftText(d)}</span>}
      </div>
      {own.length > 0 && (
        <ul className="items">
          {own.map((t) => (
            <Row key={t.key} task={t} {...board} />
          ))}
        </ul>
      )}
      {ownEvents.length > 0 && (
        <ul className="events">
          {ownEvents.map((e) => (
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
    </section>
  );
}

function Row({ task, done, canSave, onDone, onSave }: Omit<Board, "todayIso"> & { task: Task }) {
  const finished = done.has(task.key);
  return (
    <li className={`it ${task.kind} ${finished ? "done" : ""} ${task.mine ? "picked" : ""}`}>
      <button className="box" aria-pressed={finished} aria-label={`${task.title} 끝냈어요`} onClick={() => onDone(task.key)}>
        <CheckIcon size={12} />
      </button>
      <div className="txt">
        <p className="do">
          <a href={task.url} target="_blank" rel="noreferrer" title={task.sourceTitle}>
            <span>{task.title}</span>
            <span className="sr-only"> (학교 원문 새 창)</span>
          </a>
        </p>
        <div className="m">
          <span className="what">
            <b>{KIND_LABEL[task.kind]}</b>
            {task.from && <span>{task.from}</span>}
          </span>
          {task.at && <em>{task.at}</em>}
        </div>
      </div>
      {canSave && (
        <button className="keep" aria-pressed={task.saved} aria-label={task.saved ? "저장 해제" : "저장"} onClick={() => onSave(task.line)}>
          <BookmarkIcon size={15} filled={task.saved} />
        </button>
      )}
    </li>
  );
}

function Fold({ title, count, note, children }: { title: string; count: number; note: string; children: React.ReactNode }) {
  return (
    <details className="fold">
      <summary>
        <span className="fold-title">
          {title} <span className="count">{count}</span>
        </span>
        <span className="note">{note}</span>
        <ChevronIcon size={15} />
      </summary>
      <div className="fold-body">{children}</div>
    </details>
  );
}

function FoldList({ tasks, tagged = false }: { tasks: Task[]; tagged?: boolean }) {
  return (
    <ul className="fold-list">
      {tasks.map((t) => (
        <li key={t.key}>
          <span className="d">{shortDate(t.date)}</span>
          {tagged && <span className="tag end">마감</span>}
          <a href={t.url} target="_blank" rel="noreferrer">
            {t.title}
          </a>
        </li>
      ))}
    </ul>
  );
}

/** 오늘에 치는 빨간 펜 동그라미. */
function Ring() {
  return (
    <svg className="ring" viewBox="0 0 56 60" preserveAspectRatio="none" fill="none" aria-hidden>
      <path
        d="M17 8C31 1 49 7 52 26c3 18-11 31-27 30C9 55 2 41 5 26 8 13 19 5 35 6"
        stroke="currentColor"
        strokeWidth="2.6"
        strokeLinecap="round"
        vectorEffect="non-scaling-stroke"
      />
    </svg>
  );
}

/** 2주 뒤 일정. 학사일정 표 항목과 행사, 시작일을 날짜순으로 항상 보여 준다. */
function upcomingRows(tasks: Task[], events: CampusEvent[], after: string) {
  const rows = [
    ...tasks.filter((t) => t.date >= after && t.kind === "schedule").map((t) => ({ key: t.key, date: t.date, title: t.title, url: t.url, tag: "마감" as const })),
    ...events.filter((e) => e.date >= after).map((e) => ({ key: e.key, date: e.date, title: e.title, url: e.url, tag: e.tag })),
  ];
  return rows.sort((a, b) => a.date.localeCompare(b.date));
}

function addDays(d: Date, n: number): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
}

function mondayOf(d: Date): Date {
  return addDays(d, -((d.getDay() + 6) % 7));
}

function days(monday: Date): Date[] {
  return Array.from({ length: 7 }, (_, i) => addDays(monday, i));
}

function weekTitle(monday: Date): string {
  const nth = ["첫째", "둘째", "셋째", "넷째", "다섯째"][Math.ceil(monday.getDate() / 7) - 1];
  return `${monday.getMonth() + 1}월 ${nth} 주`;
}

function rangeText(a: Date, b: Date): string {
  const end = a.getMonth() === b.getMonth() ? `${b.getDate()}일` : `${b.getMonth() + 1}월 ${b.getDate()}일`;
  return `${a.getMonth() + 1}월 ${a.getDate()}일부터 ${end}까지`;
}

function leftText(d: number): string {
  return d === 0 ? "오늘" : d === 1 ? "내일" : `D-${d}`;
}

function shortDate(isoDate: string): string {
  const [, m, d] = isoDate.split("-");
  return `${Number(m)}.${Number(d)}`;
}

/** 로그인 전의 끝냈어요 표시. 이 브라우저에만 남고, 로그인하면 서버로 옮긴다. */
function readDone(): Set<string> {
  try {
    return new Set(JSON.parse(localStorage.getItem(DONE_KEY) ?? "[]") as string[]);
  } catch {
    return new Set();
  }
}

function writeDone(done: Set<string>) {
  try {
    localStorage.setItem(DONE_KEY, JSON.stringify([...done]));
  } catch {
    // 저장소를 쓸 수 없으면 화면에만 남긴다.
  }
}
