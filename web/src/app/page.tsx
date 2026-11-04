"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { api, dday, type Feed, type Line, type Me } from "@/lib/api";
import { boardCards, cleanTitle, eventsOf, iso, startsOf, tasksOf, type CampusEvent, type Kind, type Task } from "@/lib/tasks";
import { ChevronIcon } from "./icons";
import { MonthView, monthRange } from "./month";
import { addDays, Day, KIND_LABEL, shortDate, TAG_CLASS } from "./planner";

const KINDS: (Kind | "all")[] = ["all", "scholarship", "academic", "career", "dept"];
const DONE_KEY = "cuk-alimi.done";
const VIEW_KEY = "cuk-alimi.view";
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
  const [view, setView] = useState<"week" | "month">("week");
  /** 월 보기용 피드. 작년 1월 1일부터 끝나는 항목까지 담는다. 월 보기를 처음 열 때 한 번 받는다. */
  const [monthFeed, setMonthFeed] = useState<Feed | null>(null);
  const todayIso = iso(today);
  const monthFrom = `${today.getFullYear() - 1}-01-01`;
  const tasksAll = useMemo(() => tasksOf(boardCards(feed?.sections ?? []), todayIso), [feed, todayIso]);
  const monthTasksAll = useMemo(() => tasksOf(boardCards(monthFeed?.sections ?? []), todayIso), [monthFeed, todayIso]);

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
    setView(readView());
  }, []);

  useEffect(() => {
    if (view !== "month" || monthFeed) return;
    api
      .feed(monthFrom)
      .then(setMonthFeed)
      .catch((e) => setError(e.message));
  }, [view, monthFeed, monthFrom]);

  const switchView = (v: "week" | "month") => {
    setView(v);
    writeView(v);
  };

  /** 저장이나 끝냈어요 뒤에 두 피드를 같이 새로 받는다. */
  const refresh = async () => {
    setFeed(await api.feed());
    if (monthFeed) setMonthFeed(await api.feed(monthFrom));
  };

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
    // 하나라도 실패하면 로컬 체크를 지우지 않고 다음에 다시 옮긴다.
    Promise.all(pending.flatMap((t) => t.lines.filter((l) => !l.done).map((l) => api.markDone(l.id))))
      .then(() => api.feed())
      .then((f) => {
        writeDone(new Set());
        setFeed(f);
      })
      .catch(() => {});
  }, [me, feed]);

  /** 행의 모든 줄을 같이 저장하거나 푼다. 하나라도 안 된 줄이 있으면 저장 쪽이다. */
  const toggleSave = async (lines: Line[]) => {
    setError(null);
    try {
      const saving = lines.some((l) => !l.saved);
      await Promise.all(lines.filter((l) => l.saved !== saving).map((l) => (saving ? api.save(l.id) : api.unsave(l.id))));
      await refresh();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const toggleDone = async (key: string) => {
    const task = tasksAll.find((t) => t.key === key) ?? monthTasksAll.find((t) => t.key === key);
    if (me && task) {
      setError(null);
      try {
        // 행의 모든 줄에 보낸다. 첫 줄만 보내면 나머지 줄의 푸시와 캘린더가 계속 나간다.
        await Promise.all(task.lines.filter((l) => l.done === task.done).map((l) => (task.done ? api.unmarkDone(l.id) : api.markDone(l.id))));
        await refresh();
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

  const undatedMemo = (
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
  );
  const savedMemo = (
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
  );

  const monthSections = monthFeed?.sections ?? [];
  const monthShown = monthTasksAll.filter((t) => inKind(t.kind));
  const monthEvents = [...eventsOf(monthSections), ...startsOf(monthSections, monthFrom)].filter((e) => inKind(e.kind));
  const monthBoard = { ...board, done: me ? new Set(monthTasksAll.filter((t) => t.done).map((t) => t.key)) : done };
  const range = monthRange([...monthTasksAll.map((t) => t.date), ...monthEvents.map((e) => e.date)], todayIso);

  return (
    <main id="main" className="planner">
      <div className="head">
        <div>
          <h1>{view === "week" ? weekTitle(monday) : "월간 일정"}</h1>
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
            <div className="seg" role="group" aria-label="보기">
              <button aria-pressed={view === "week"} onClick={() => switchView("week")}>
                주
              </button>
              <button aria-pressed={view === "month"} onClick={() => switchView("month")}>
                월
              </button>
            </div>
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

      {feed && view === "month" && (
        <>
          {monthFeed ? (
            <MonthView tasks={monthShown} events={monthEvents} range={range} board={monthBoard} />
          ) : (
            <div className="spread loading" aria-label="불러오는 중" />
          )}
          <div className="spread two memos">
            {undatedMemo}
            {savedMemo}
          </div>
        </>
      )}

      {feed && view === "week" && (
        <>
          <div className="spread">
            {days(monday).map((d) => (
              <Day key={iso(d)} date={d} tasks={shown} events={events} {...board} />
            ))}
            {undatedMemo}
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
            {savedMemo}
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
        </>
      )}

      {feed && (
        <>
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

/** 2주 뒤 일정. 학사일정 표 항목과 행사, 시작일을 날짜순으로 항상 보여 준다. */
function upcomingRows(tasks: Task[], events: CampusEvent[], after: string) {
  const rows = [
    ...tasks.filter((t) => t.date >= after && t.kind === "schedule").map((t) => ({ key: t.key, date: t.date, title: t.title, url: t.url, tag: "마감" as const })),
    ...events.filter((e) => e.date >= after).map((e) => ({ key: e.key, date: e.date, title: e.title, url: e.url, tag: e.tag })),
  ];
  return rows.sort((a, b) => a.date.localeCompare(b.date));
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

/** 고른 보기(주, 월). 저장소를 못 쓰면 주간으로 연다. */
function readView(): "week" | "month" {
  try {
    return localStorage.getItem(VIEW_KEY) === "month" ? "month" : "week";
  } catch {
    return "week";
  }
}

function writeView(v: "week" | "month") {
  try {
    localStorage.setItem(VIEW_KEY, v);
  } catch {
    // 저장소를 쓸 수 없으면 이번에만 바꾼다.
  }
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
