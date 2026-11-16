"use client";

// 홈 목록의 행과 날짜 표시.

import { dday, type Line } from "@/lib/api";
import { dayText, type Kind, type Task } from "@/lib/tasks";
import { useState } from "react";
import { BookmarkIcon, CheckIcon } from "./icons";

/** 학사일정 표와 학사 공지는 출처만 다르다. 학생에게는 둘 다 학사다. */
export const KIND_LABEL: Record<Kind, string> = { schedule: "학사", scholarship: "장학", academic: "학사", career: "취업", dept: "학과" };

export type Board = {
  todayIso: string;
  done: Set<string>;
  canSave: boolean;
  onDone: (key: string) => void;
  onSave: (lines: Line[]) => void;
  /** 그림 날짜 신고. 로그인했을 때만 있다. */
  onReport?: (lines: Line[]) => void;
};

/**
 * 행. 체크, 할 일, 남은 날과 날짜, 로그인했으면 책갈피 순이다.
 * 그림에서 읽어 사람 확인 없이 공개한 날짜가 섞인 행에만 작은 "확인" 표시가 붙고, 누르면 그 사실과 날짜 신고가 나온다.
 * 내게 맞는 일은 제목에 형광펜을 긋고, 그은 이유는 제목의 title에 둔다.
 */
export function Row({ task, todayIso, done, canSave, onDone, onSave, onReport }: Board & { task: Task }) {
  const finished = done.has(task.key);
  const [open, setOpen] = useState(false);
  const [reported, setReported] = useState(false);
  const check = task.fromImage;
  const d = dday(task.date);
  return (
    <li className={`it ${finished ? "done" : ""} ${task.mine ? "picked" : ""}`}>
      <button className="box" aria-pressed={finished} aria-label={`${task.title} 끝냈어요`} onClick={() => onDone(task.key)}>
        <CheckIcon size={13} />
      </button>
      <div className="txt">
        <p className="do">
          <a href={task.url} target="_blank" rel="noreferrer" title={task.sourceTitle}>
            <span title={task.mine ? (task.mineReason ?? undefined) : undefined}>{task.title}</span>
            <span className="sr-only">
              {task.mine ? " 내게 맞음" : ""} (학교 원문 새 창)
            </span>
          </a>
        </p>
        <div className="m">
          <span className="what">
            <b>{KIND_LABEL[task.kind]}</b>
            {task.from && <span>{task.from}</span>}
          </span>
          {task.at && <em>{task.at}</em>}
          {check && (
            <button type="button" className="check-tag" aria-expanded={open} onClick={() => setOpen(!open)}>
              확인
            </button>
          )}
        </div>
        {check && open && (
          <div className="check-body">
            <p>공지 그림에서 읽은 날짜예요. 원문과 다르면 알려 주세요.</p>
            {onReport && !reported && (
              <button
                type="button"
                className="link"
                onClick={() => {
                  onReport(task.lines.filter((l) => l.fromImage));
                  setReported(true);
                }}
              >
                날짜가 달라요
              </button>
            )}
            {reported && <p>알려 주셔서 고마워요. 그 날짜는 내리고 운영자가 확인해요.</p>}
          </div>
        )}
      </div>
      <div className={`when ${d <= 2 ? "soon" : ""}`}>
        <b>{leftText(d)}</b>
        <span>{dayText(task.date, todayIso)}</span>
      </div>
      {canSave && (
        <button className="keep" aria-pressed={task.saved} aria-label={task.saved ? "저장 해제" : "저장"} onClick={() => onSave(task.lines)}>
          <BookmarkIcon size={16} filled={task.saved} />
        </button>
      )}
    </li>
  );
}

export function leftText(d: number): string {
  return d === 0 ? "오늘" : d === 1 ? "내일" : `D-${d}`;
}

export function shortDate(isoDate: string): string {
  const [, m, d] = isoDate.split("-");
  return `${Number(m)}.${Number(d)}`;
}
