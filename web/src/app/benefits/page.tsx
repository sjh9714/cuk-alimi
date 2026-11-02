"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api, dday, formatDate, type Benefit, type Me } from "@/lib/api";
import { CheckIcon, ChevronIcon, ExternalIcon } from "../icons";

const CATEGORY: Record<string, string> = {
  TOOL: "개발·학습 도구",
  SUBSCRIPTION: "구독",
  SCHOLARSHIP: "장학·학비",
  SCHOOL: "학교",
  TRANSPORT: "교통",
  FINANCE: "금융",
  CULTURE: "문화",
};

/** 학생 누구나 볼 것부터. 개발 도구는 비전공 학생이 덜 헤매게 맨 아래에 둔다. */
const ORDER = ["SCHOLARSHIP", "SCHOOL", "SUBSCRIPTION", "TOOL"];

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

  const present = new Set((benefits ?? []).map((b) => b.category));
  const categories = [...ORDER.filter((c) => present.has(c)), ...[...present].filter((c) => !ORDER.includes(c))];
  const checked = (benefits ?? []).filter((b) => b.checked).length;
  return (
    <main id="main">
      <div className="page-head">
        <h1>학생 혜택</h1>
        <p className="sub">
          대학생 자격으로 받는 것만 모았어요. 항목을 누르면 조건, 방법, 출처가 펼쳐져요.
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
          <section key={c} className={`benefit-group cat-${c.toLowerCase()}`} aria-labelledby={`cat-${c}`}>
            <h2 id={`cat-${c}`} className="band">
              <span className="band-in">
                {CATEGORY[c] ?? c} <span>{list.length}개</span>
              </span>
            </h2>
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
    <li className={`cell benefit ${canCheck ? "" : "plain"} ${hot ? "hot" : ""} ${ended ? "ended" : ""}`}>
      {canCheck && (
        <button className="box" aria-pressed={b.checked} aria-label={`${b.name} 받는 중`} onClick={() => onToggle(b)}>
          <CheckIcon size={12} />
        </button>
      )}
      <details>
        <summary>
          <span className="txt">
            <span className="name">
              <span>{b.name}</span>
            </span>
            <span className="desc">{b.summary}</span>
            {b.stale && <b className="stale">재확인 필요</b>}
          </span>
          {d !== null && <span className="left">{ddayLabel(b.endsOn!)}</span>}
          <ChevronIcon size={16} />
        </summary>
        <dl>
          {b.price && (
            <>
              <dt>가격</dt>
              <dd>{b.price}</dd>
            </>
          )}
          <dt>조건</dt>
          <dd>{b.condition}</dd>
          <dt>방법</dt>
          <dd>{b.howTo}</dd>
          <dt>갱신</dt>
          <dd>{b.renewalRule}</dd>
          <dt>출처</dt>
          <dd>
            <a href={b.sourceUrl} target="_blank" rel="noreferrer">
              {b.sourceName} <ExternalIcon size={12} />
            </a>{" "}
            {formatDate(b.verifiedOn)} 확인
          </dd>
        </dl>
      </details>
    </li>
  );
}

function ddayLabel(endsOn: string): string {
  const d = dday(endsOn);
  return d < 0 ? "끝남" : d === 0 ? "오늘까지" : `D-${d}`;
}
