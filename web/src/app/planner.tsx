"use client";

// 주간 판과 월 달력이 함께 쓰는 행, 표시, 날짜 계산.

import { dday, type Line } from "@/lib/api";
import { iso, type CampusEvent, type Kind, type Task } from "@/lib/tasks";
import { BookmarkIcon, CheckIcon } from "./icons";

/** 학사일정 표와 학사 공지는 출처만 다르다. 학생에게는 둘 다 학사다. */
export const KIND_LABEL: Record<Kind, string> = { schedule: "학사", scholarship: "장학", academic: "학사", career: "취업", dept: "학과" };
export const WEEKDAY = ["일", "월", "화", "수", "목", "금", "토"];
export const TAG_CLASS = { 시작: "start", 마감: "end", 행사: "event" };

export type Board = {
  todayIso: string;
  done: Set<string>;
  canSave: boolean;
  onDone: (key: string) => void;
  onSave: (lines: Line[]) => void;
};

export function Day({ date, tasks, events, todayIso, ...board }: Board & { date: Date; tasks: Task[]; events: CampusEvent[] }) {
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

export function Row({ task, done, canSave, onDone, onSave }: Omit<Board, "todayIso"> & { task: Task }) {
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
        <button className="keep" aria-pressed={task.saved} aria-label={task.saved ? "저장 해제" : "저장"} onClick={() => onSave(task.lines)}>
          <BookmarkIcon size={15} filled={task.saved} />
        </button>
      )}
    </li>
  );
}

/** 오늘에 치는 빨간 펜 동그라미. */
export function Ring() {
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

export function addDays(d: Date, n: number): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
}

export function leftText(d: number): string {
  return d === 0 ? "오늘" : d === 1 ? "내일" : `D-${d}`;
}

export function shortDate(isoDate: string): string {
  const [, m, d] = isoDate.split("-");
  return `${Number(m)}.${Number(d)}`;
}
