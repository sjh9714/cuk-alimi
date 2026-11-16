"use client";

import { useEffect, useMemo, useState } from "react";
import { api, DEFAULT_TOPICS, type Feed, type Line, type Me, type Section } from "@/lib/api";
import { cleanTitle, dayText, eventsOf, groupByWhen, iso, partsText, splitByInterest, tasksOf, topicParts, WEEKDAY, type CampusEvent, type Task } from "@/lib/tasks";
import { ChevronIcon } from "./icons";
import { Row, shortDate } from "./planner";

const DONE_KEY = "cuk-alimi.done";
/** "그 뒤" 묶음에서 먼저 보여 주는 행 수. 학사일정이 다음 학기까지 이어져 길다. */
const LATER_MAX = 8;
/** 관심 밖과 달리 자격으로 접힌 구간. 이름은 접힌 줄의 내역에 쓴다. */
const NARROW: { key: Section["key"]; label: string }[] = [
  { key: "region", label: "지역 장학" },
  { key: "group", label: "특정 대상" },
  { key: "dept", label: "다른 학과" },
];

/** 정적 HTML에는 빌드한 날이 박힌다. 오늘은 브라우저에서 처음 그릴 때 정한다. */
export default function Home() {
  const [today, setToday] = useState<Date | null>(null);
  useEffect(() => setToday(new Date()), []);
  if (!today) {
    return (
      <main id="main">
        <div className="spread loading" aria-label="불러오는 중" />
      </main>
    );
  }
  return <Planner today={today} />;
}

type Entry = { key: string; date: string; task?: Task; event?: CampusEvent };

function Planner({ today }: { today: Date }) {
  const [feed, setFeed] = useState<Feed | null>(null);
  const [me, setMe] = useState<Me | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<Set<string>>(new Set());
  const [allLater, setAllLater] = useState(false);
  const todayIso = iso(today);
  const sections = useMemo(() => feed?.sections ?? [], [feed]);
  // 로그인 전에는 기본 관심 주제 다섯 개로 거른다 (ADR 015).
  const interests = me ? me.interests : DEFAULT_TOPICS;
  const { main, outside } = useMemo(() => splitByInterest(sections, interests, todayIso), [sections, interests, todayIso]);
  const tasksAll = useMemo(() => [...main, ...outside], [main, outside]);

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

  /** 저장이나 끝냈어요 뒤에 피드를 새로 받는다. */
  const refresh = async () => {
    setFeed(await api.feed());
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
    const task = tasksAll.find((t) => t.key === key);
    if (me && task) {
      setError(null);
      try {
        // 행의 모든 줄에 보낸다. 첫 줄만 보내면 나머지 줄의 메일과 캘린더가 계속 나간다.
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

  /** 그림 날짜 신고. 서버가 받으면 그 날짜를 내린다 (ADR 010). 확인을 따로 묻지 않고, 화면에 고맙다는 말만 남긴다. */
  const reportDate = async (lines: Line[]) => {
    setError(null);
    try {
      await Promise.all(lines.map((l) => api.reportDate(l.id)));
      await refresh();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const finishedKeys = serverDone ?? done;
  const board = { todayIso, done: finishedKeys, canSave: !!me, onDone: toggleDone, onSave: toggleSave, onReport: me ? reportDate : undefined };

  // 할 일과 행사를 날짜순 한 줄기로 합친다. 같은 날에는 할 일이 먼저다.
  const entries: Entry[] = [
    ...main.map((t): Entry => ({ key: t.key, date: t.date, task: t })),
    ...eventsOf(sections).map((e): Entry => ({ key: e.key, date: e.date, event: e })),
  ].sort((a, b) => a.date.localeCompare(b.date) || Number(!!a.event) - Number(!!b.event));
  const groups = groupByWhen(entries, todayIso);
  const openCount = (key: "week" | "next") => (groups.find((g) => g.key === key)?.rows ?? []).filter((r) => r.task && !finishedKeys.has(r.task.key)).length;
  const weekCount = openCount("week");
  const nextCount = openCount("next");

  const outsideRows = outside.filter((t) => t.date >= todayIso);
  const narrow = NARROW.map((n) => ({ ...n, tasks: tasksOf(sections.find((s) => s.key === n.key)?.cards ?? [], todayIso).filter((t) => t.date >= todayIso) })).filter((n) => n.tasks.length > 0);
  const narrowCount = narrow.reduce((sum, n) => sum + n.tasks.length, 0);
  const undated = sections.find((s) => s.key === "undated")?.cards ?? [];
  const mine = me ? "내 마감" : "마감";

  return (
    <main id="main">
      <div className="head">
        <h1>
          {today.getMonth() + 1}월 {today.getDate()}일 {WEEKDAY[today.getDay()]}요일
        </h1>
        {feed && (
          <p className="say" aria-live="polite">
            {weekCount > 0 ? (
              <>
                이번 주 {mine} <b>{weekCount}건</b>
              </>
            ) : (
              "이번 주에 끝나는 일은 없어요"
            )}
            {nextCount > 0 && (
              <>
                , 다음 주 <b>{nextCount}건</b>
              </>
            )}
            {outsideRows.length > 0 && (
              <>
                {" "}
                <a href="#outside" onClick={openOutside}>
                  관심 밖 {outsideRows.length}건
                </a>
              </>
            )}
          </p>
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

      {feed &&
        groups.map((g) => {
          const cut = g.key === "later" && !allLater && g.rows.length > LATER_MAX;
          const rows = cut ? g.rows.slice(0, LATER_MAX) : g.rows;
          return (
            <section key={g.key} className="group" aria-labelledby={`group-${g.key}`}>
              <h2 id={`group-${g.key}`}>{g.label}</h2>
              <ul className="items">
                {rows.map((r) => (r.task ? <Row key={r.key} task={r.task} {...board} /> : <EventRow key={r.key} event={r.event!} todayIso={todayIso} />))}
              </ul>
              {cut && (
                <button className="text more" onClick={() => setAllLater(true)}>
                  더 보기 {g.rows.length - LATER_MAX}건
                </button>
              )}
            </section>
          );
        })}

      {feed && (
        <div className="folds">
          {outsideRows.length > 0 && (
            <Fold id="outside" title="관심 밖" count={outsideRows.length} note={partsText(topicParts(outsideRows), 3)} open={groups.length === 0}>
              <ul className="items">
                {outsideRows.map((t) => (
                  <Row key={t.key} task={t} {...board} />
                ))}
              </ul>
            </Fold>
          )}
          {narrowCount > 0 && (
            <Fold title="다른 대상" count={narrowCount} note={partsText(narrow.map((n) => ({ label: n.label, count: n.tasks.length })), 3)}>
              {narrow.map((n) => (
                <div key={n.key}>
                  <h3>{n.label}</h3>
                  <FoldList tasks={n.tasks} />
                </div>
              ))}
            </Fold>
          )}
          {undated.length > 0 && (
            <Fold title="날짜 확인 중" count={undated.length} note="그림에만 날짜가 있는 공지">
              <ul className="fold-list plain">
                {undated.map((c) => (
                  <li key={c.key}>
                    <a href={c.url} target="_blank" rel="noreferrer">
                      {cleanTitle(c.title)}
                    </a>
                  </li>
                ))}
              </ul>
            </Fold>
          )}
        </div>
      )}
    </main>
  );
}

/** 요약 줄의 "관심 밖 N건"을 누르면 접힌 줄을 펼쳐서 보여 준다. */
function openOutside() {
  const fold = document.getElementById("outside");
  if (fold instanceof HTMLDetailsElement) fold.open = true;
}

/** 행사. 할 일이 아니라서 체크와 남은 날 없이 회색으로만 적는다. */
function EventRow({ event, todayIso }: { event: CampusEvent; todayIso: string }) {
  return (
    <li className="it event">
      <span aria-hidden />
      <div className="txt">
        <p className="do">
          <a href={event.url} target="_blank" rel="noreferrer">
            {event.title}
          </a>
        </p>
        <div className="m">
          <span className="what">
            <b>행사</b>
          </span>
          {event.at && <em>{event.at}</em>}
        </div>
      </div>
      <div className="when">
        <span>{dayText(event.date, todayIso)}</span>
      </div>
    </li>
  );
}

function Fold({ id, title, count, note, open, children }: { id?: string; title: string; count: number; note: string; open?: boolean; children: React.ReactNode }) {
  return (
    <details className="fold" id={id} open={open || undefined}>
      <summary>
        <span className="fold-title">
          {title} {count}건
        </span>
        <span className="note">{note}</span>
        <ChevronIcon size={15} />
      </summary>
      <div className="fold-body">{children}</div>
    </details>
  );
}

function FoldList({ tasks }: { tasks: Task[] }) {
  return (
    <ul className="fold-list">
      {tasks.map((t) => (
        <li key={t.key}>
          <span className="d">{shortDate(t.date)}</span>
          <a href={t.url} target="_blank" rel="noreferrer">
            {t.title}
          </a>
        </li>
      ))}
    </ul>
  );
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
