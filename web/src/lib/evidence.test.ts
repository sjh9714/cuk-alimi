import { describe, expect, it } from "vitest";
import type { Line } from "./api";
import { evidencesOf, needsCheck, splitEvidence } from "./evidence";

function line(extra: Partial<Line> = {}): Line {
  return { id: 1, label: null, startsOn: null, endsOn: "2026-11-13", startsAt: null, endsAt: null, kind: "ACTION", saved: false, done: false, fromImage: false, evidence: null, foundBy: "rule", doubt: false, ...extra };
}

describe("splitEvidence", () => {
  it("날짜 글자만 떼어 낸다", () => {
    expect(splitEvidence({ text: "신청기간 : 11. 3.(화) ~ 11. 13.(금) 17:00", match: "11. 3.(화) ~ 11. 13.(금)" })).toEqual({
      before: "신청기간 : ",
      mark: "11. 3.(화) ~ 11. 13.(금)",
      after: " 17:00",
    });
  });

  it("날짜 글자가 없거나 글 안에 없으면 형광펜 없이 글만 준다", () => {
    expect(splitEvidence({ text: "신청기간 안내", match: null })).toEqual({ before: "신청기간 안내", mark: "", after: "" });
    expect(splitEvidence({ text: "신청기간 안내", match: "11. 13." })).toEqual({ before: "신청기간 안내", mark: "", after: "" });
  });
});

describe("evidencesOf", () => {
  it("합친 줄의 같은 근거는 한 번만, 다른 근거는 따로 남긴다", () => {
    const a = { text: "서강대학교 | 11. 9.(월) 17:00", match: "11. 9.(월)" };
    const b = { text: "고려대학교 | 11. 9.(월) 17:00", match: "11. 9.(월)" };
    expect(evidencesOf([line({ evidence: a }), line({ evidence: { ...a } }), line({ evidence: b }), line()])).toEqual([a, b]);
  });
});

describe("needsCheck", () => {
  it("규칙이 읽은 줄은 표시가 없고, 그림·AI가 읽었거나 AI가 의심한 줄은 확인을 단다", () => {
    expect(needsCheck(line())).toBe(false);
    expect(needsCheck(line({ foundBy: "image", fromImage: true }))).toBe(true);
    expect(needsCheck(line({ foundBy: "ai" }))).toBe(true);
    expect(needsCheck(line({ doubt: true }))).toBe(true);
  });
});
