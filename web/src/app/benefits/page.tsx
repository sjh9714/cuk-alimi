"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api, dday, formatDate, type Benefit, type Me } from "@/lib/api";
import { CheckIcon, ExternalIcon } from "../icons";

const CATEGORY: Record<string, string> = {
  TOOL: "개발·학습 도구",
  SUBSCRIPTION: "구독",
  SCHOLARSHIP: "장학·학비",
  SCHOOL: "학교",
  TRANSPORT: "교통",
  FINANCE: "금융",
  CULTURE: "문화",
};

export default function Benefits() {
  const [benefits, setBenefits] = useState<Benefit[] | null>(null);
  const [me, setMe] = useState<Me | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () =>
    Promise.all([api.benefits(), api.me()])
      .then(([b, m]) => {
        setBenefits(b);
        setMe(m);
      })
      .catch((e) => setError(e.message));

  useEffect(() => {
    load();
  }, []);

  const toggle = async (b: Benefit) => {
    if (!me) return;
    try {
      if (b.checked) await api.uncheck(b.id);
      else await api.check(b.id);
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const categories = Array.from(new Set((benefits ?? []).map((b) => b.category)));
  const checked = (benefits ?? []).filter((b) => b.checked).length;
  return (
    <main id="main">
      <div className="page-head">
        <h1>학생 혜택</h1>
        <p className="sub">
          대학생 자격으로 받는 것만 모았어요. 항목마다 출처와 확인한 날짜가 붙어 있어요.
          {me ? (
            checked > 0 && ` 지금 받는 혜택 ${checked}개를 체크해 두었어요.`
          ) : (
            <>
              {" "}
              <Link href="/settings/">로그인</Link>하면 받는 중인 혜택을 체크해 둘 수 있어요.
            </>
          )}
        </p>
      </div>
      {error && (
        <p className="error-line" role="alert">
          {error}
        </p>
      )}
      {benefits === null && !error && <div className="spread loading" aria-label="불러오는 중" />}
      {categories.map((c) => {
        const list = (benefits ?? []).filter((b) => b.category === c);
        return (
          <section key={c} aria-labelledby={`cat-${c}`}>
            <div className="next">
              <h2 id={`cat-${c}`}>{CATEGORY[c] ?? c}</h2>
              <span>{list.length}개</span>
            </div>
            <ul className="spread two">
              {list.map((b) => (
                <BenefitCell key={b.id} benefit={b} canCheck={!!me} onToggle={toggle} />
              ))}
            </ul>
          </section>
        );
      })}
    </main>
  );
}

function BenefitCell({ benefit: b, canCheck, onToggle }: { benefit: Benefit; canCheck: boolean; onToggle: (b: Benefit) => void }) {
  const d = b.endsOn ? dday(b.endsOn) : null;
  const hot = d !== null && d >= 0 && d <= 2;
  const ended = d !== null && d < 0;
  return (
    <li className={`cell benefit ${hot ? "hot" : ""} ${ended ? "ended" : ""}`}>
      <div className={`b-top ${canCheck ? "" : "plain"}`}>
        {canCheck && (
          <button className="box" aria-pressed={b.checked} aria-label={`${b.name} 받는 중`} onClick={() => onToggle(b)}>
            <CheckIcon size={12} />
          </button>
        )}
        <div className="txt">
          <h3>
            <span>{b.name}</span>
          </h3>
          <p>{b.summary}</p>
        </div>
        <span className="left">{d === null ? "상시" : ddayLabel(b.endsOn!)}</span>
      </div>
      <div className="m">
        {b.price && <em>{b.price}</em>}
        <a href={b.sourceUrl} target="_blank" rel="noreferrer">
          {b.sourceName} <ExternalIcon size={12} />
        </a>
        {b.stale ? <b className="stale">재확인 필요</b> : <span>{formatDate(b.verifiedOn)} 확인</span>}
        <details>
          <summary>조건과 방법</summary>
          <dl>
            <dt>조건</dt>
            <dd>{b.condition}</dd>
            <dt>방법</dt>
            <dd>{b.howTo}</dd>
            <dt>갱신</dt>
            <dd>{b.renewalRule}</dd>
          </dl>
        </details>
      </div>
    </li>
  );
}

function ddayLabel(endsOn: string): string {
  const d = dday(endsOn);
  return d < 0 ? "끝남" : d === 0 ? "오늘까지" : `D-${d}`;
}
