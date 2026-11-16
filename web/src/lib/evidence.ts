// 근거 (ADR 016). 날짜를 읽은 글에서 날짜 글자만 떼어 형광펜을 그을 수 있게 나눈다.

import type { Evidence, Line } from "./api";

export type EvidenceParts = { before: string; mark: string; after: string };

/** 근거 글을 날짜 앞, 날짜, 날짜 뒤로 나눈다. 날짜 글자가 없거나 글 안에 없으면 전부 before다. */
export function splitEvidence(e: Evidence): EvidenceParts {
  const at = e.match ? e.text.indexOf(e.match) : -1;
  if (!e.match || at < 0) return { before: e.text, mark: "", after: "" };
  return { before: e.text.slice(0, at), mark: e.match, after: e.text.slice(at + e.match.length) };
}

/** 한 행에 합친 줄들의 근거. 같은 글은 한 번만 보여 준다. 표의 한 행에서 날짜 두 개를 읽었으면 날짜마다 따로 남긴다. */
export function evidencesOf(lines: Line[]): Evidence[] {
  const seen = new Set<string>();
  const out: Evidence[] = [];
  for (const line of lines) {
    if (!line.evidence) continue;
    const key = `${line.evidence.text}\n${line.evidence.match ?? ""}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(line.evidence);
  }
  return out;
}

/** 사람 확인 없이 공개했거나 AI가 의심한 줄인가. 이런 줄이 든 행에는 "확인"을 달고 신고를 받는다. */
export function needsCheck(line: Line): boolean {
  return line.foundBy !== "rule" || line.doubt;
}
