import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { describe, expect, it } from "vitest";
import type { Card } from "./api";
import { tasksOf } from "./tasks";

/**
 * 화면 행 제목 채점. 서버 시험(RowCardsTest)이 정답셋 공지를 실제 피드 코드로 카드로 만들어 두면,
 * 여기서 홈과 같은 함수(tasksOf)로 행을 만들고 gold/rows.json과 날짜마다 비교한다.
 * 그날 그 공지의 행이 정확히 하나이고, 36자 이하에 표의 기호가 없고, 정답의 필수 낱말(must)을 모두 담아야 맞다.
 * 정답이나 허용 표현과 글자까지 같은 수(exact)도 따로 센다. 정답에 없는 날의 행은 "남는 행"으로 센다.
 * 결과는 src/main/resources/quality/rows.json에 공개한다. 달라지면 실패하고, QUALITY_UPDATE=1이면 다시 쓴다.
 */
type GoldRow = { date: string; title: string; accept?: string[]; must?: string[] };
type Gold = { notices: Record<string, { set: "dev" | "fresh"; rows: GoldRow[] }> };
type Dumped = { section: string; collapsed: boolean; card: Card }[];

const root = resolve(__dirname, "../../..");
const gold: Gold = JSON.parse(readFileSync(resolve(root, "src/test/resources/fixtures/gold/rows.json"), "utf8"));
const cards: Record<string, Dumped> = JSON.parse(readFileSync(resolve(__dirname, "__fixtures__/gold-cards.json"), "utf8"));
const published = resolve(root, "src/main/resources/quality/rows.json");

const norm = (s: string) => s.replace(/[\s·,()]/g, "");
const SYMBOL = /[●○■□◆◇★☆•ㆍ*<>]/;

/** 홈이 행으로 그리는 카드. 행사와 날짜 확인 중 구간은 행이 아니다. */
function rowsOf(no: string) {
  const shown = (cards[no] ?? []).filter((d) => d.section !== "events" && d.section !== "undated").map((d) => d.card);
  return tasksOf(shown, "2026-01-01");
}

function score(set: "dev" | "fresh") {
  let total = 0;
  let ok = 0;
  let exact = 0;
  let extra = 0;
  const misses: string[] = [];
  for (const [no, g] of Object.entries(gold.notices)) {
    if (g.set !== set) continue;
    const rows = rowsOf(no);
    const dates = new Set(g.rows.map((r) => r.date));
    for (const want of g.rows) {
      total++;
      const got = rows.filter((r) => r.date === want.date).map((r) => r.title);
      const one = got.length === 1 ? got[0] : null;
      const same = one !== null && [want.title, ...(want.accept ?? [])].some((t) => norm(t) === norm(one));
      const fits = one !== null && one.length <= 36 && !SYMBOL.test(one) && (want.must ?? []).every((m) => norm(one).includes(norm(m)));
      if (same) exact++;
      if (same || fits) ok++;
      else misses.push(`${no} ${want.date} 정답 "${want.title}" 필수 ${JSON.stringify(want.must ?? [])} 화면 ${JSON.stringify(got)}`);
    }
    for (const r of rows.filter((r) => !dates.has(r.date))) {
      extra++;
      misses.push(`${no} ${r.date} 남는 행 "${r.title}"`);
    }
  }
  const ratio = (n: number) => (total === 0 ? 0 : Math.round((n * 1000) / total) / 1000);
  return { total, ok, ratio: ratio(ok), exact, exactRatio: ratio(exact), extra, misses };
}

describe("화면 행 제목 채점", () => {
  it("공개한 점수가 이번 결과와 같다", () => {
    const dev = score("dev");
    const fresh = score("fresh");
    const report = resolve(root, "build/reports/rows/misses.txt");
    mkdirSync(dirname(report), { recursive: true });
    writeFileSync(report, ["[dev]", ...dev.misses, "", "[fresh]", ...fresh.misses, ""].join("\n"));
    const now = {
      dev: { total: dev.total, ok: dev.ok, ratio: dev.ratio, exact: dev.exact, exactRatio: dev.exactRatio, extra: dev.extra },
      fresh: { total: fresh.total, ok: fresh.ok, ratio: fresh.ratio, exact: fresh.exact, exactRatio: fresh.exactRatio, extra: fresh.extra },
    };
    console.log("rows", JSON.stringify(now));
    if (process.env.QUALITY_UPDATE || !existsSync(published)) {
      writeFileSync(published, JSON.stringify(now, null, 2) + "\n");
      return;
    }
    expect(JSON.parse(readFileSync(published, "utf8")), "공개 행 점수가 다르다. QUALITY_UPDATE=1로 다시 쓴다").toEqual(now);
  });
});
