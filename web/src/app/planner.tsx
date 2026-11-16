"use client";

// 홈 목록의 행과 날짜 표시.

import { dday, type Line } from "@/lib/api";
import { evidencesOf, needsCheck, splitEvidence } from "@/lib/evidence";
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
  /** 날짜 신고. 로그인했을 때만 있다. */
  onReport?: (lines: Line[]) => void;
};

/**
 * 행. 체크, 할 일, 남은 날과 날짜, 로그인했으면 책갈피 순이다.
 * 날짜를 누르면 근거가 펼쳐진다 (ADR 016). 공지에서 그 날짜를 읽은 글을 그대로 보여 주고 날짜 글자에 형광펜을 긋는다.
 * 사람 확인 없이 공개한 날짜(그림, AI가 찾은 문장)나 AI가 의심한 날짜가 섞인 행에는 "확인"이 붙고, 근거 안에서 신고를 받는다.
 */
export function Row({ task, todayIso, done, canSave, onDone, onSave, onReport }: Board & { task: Task }) {
  const finished = done.has(task.key);
  const [open, setOpen] = useState(false);
  const [reported, setReported] = useState(false);
  const d = dday(task.date);
  const evidences = evidencesOf(task.lines);
  const doubtful = task.lines.filter(needsCheck);
  const hasPanel = evidences.length > 0 || !!task.eligibilityEvidence || task.check;
  const when = (
    <>
      <b>{leftText(d)}</b>
      <span>{dayText(task.date, todayIso)}</span>
    </>
  );
  return (
    <li className={`it ${finished ? "done" : ""}`}>
      <button className="box" aria-pressed={finished} aria-label={`${task.title} 끝냈어요`} onClick={() => onDone(task.key)}>
        <CheckIcon size={13} />
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
          {task.check && (
            <button type="button" className="check-tag" aria-expanded={open} onClick={() => setOpen(!open)}>
              확인
            </button>
          )}
        </div>
        {hasPanel && open && (
          <div className="check-body">
            {evidences.map((e, i) => {
              const part = splitEvidence(e);
              return (
                <p key={i} className="ev">
                  {part.before}
                  {part.mark && <mark>{part.mark}</mark>}
                  {part.after}
                </p>
              );
            })}
            {task.eligibilityEvidence && (
              <p className="ev">
                <b>대상</b> {task.eligibilityEvidence}
              </p>
            )}
            <p className="src">{sourceNote(doubtful, evidences.length > 0)}</p>
            {doubtful.length > 0 && onReport && !reported && (
              <button
                type="button"
                className="link"
                onClick={() => {
                  onReport(doubtful);
                  setReported(true);
                }}
              >
                날짜가 달라요
              </button>
            )}
            {reported && <p>고마워요. 그 날짜는 내렸어요.</p>}
          </div>
        )}
      </div>
      {hasPanel ? (
        <button type="button" className={`when ${d <= 2 ? "soon" : ""}`} aria-expanded={open} aria-label={`${leftText(d)}, 근거 보기`} onClick={() => setOpen(!open)}>
          {when}
        </button>
      ) : (
        <div className={`when ${d <= 2 ? "soon" : ""}`}>{when}</div>
      )}
      {canSave && (
        <button className="keep" aria-pressed={task.saved} aria-label={task.saved ? "저장 해제" : "저장"} onClick={() => onSave(task.lines)}>
          <BookmarkIcon size={16} filled={task.saved} />
        </button>
      )}
    </li>
  );
}

/** 근거 아래 한 줄. 어디서 읽은 날짜인지, 확인이 왜 붙었는지를 적는다. */
function sourceNote(doubtful: Line[], hasEvidence: boolean): string {
  if (doubtful.some((l) => l.foundBy === "ai")) return "규칙이 놓친 문장을 AI가 찾았고, 날짜는 이 문장에서 읽었어요.";
  if (doubtful.some((l) => l.foundBy === "image")) return "공지 그림을 받아 적은 글에서 읽은 날짜예요.";
  if (doubtful.some((l) => l.doubt)) return "신청 마감이 아닐 수 있어요. 원문을 확인해 주세요.";
  return hasEvidence ? "공지에서 이 날짜를 읽은 자리예요." : "공지에 적힌 대상 조건이에요.";
}

export function leftText(d: number): string {
  return d === 0 ? "오늘" : d === 1 ? "내일" : `D-${d}`;
}

export function shortDate(isoDate: string): string {
  const [, m, d] = isoDate.split("-");
  return `${Number(m)}.${Number(d)}`;
}
