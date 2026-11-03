"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api, type Me, type Sido } from "@/lib/api";

export default function Settings() {
  const [me, setMe] = useState<Me | null | undefined>(undefined);
  const [email, setEmail] = useState("");
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [entryYear, setEntryYear] = useState("");
  const [semester, setSemester] = useState("");
  const [major, setMajor] = useState("");
  const [secondMajor, setSecondMajor] = useState("");
  const [sido, setSido] = useState("");
  const [sigungu, setSigungu] = useState("");
  const [regions, setRegions] = useState<Sido[]>([]);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [digestNote, setDigestNote] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);

  useEffect(() => {
    const flag = new URLSearchParams(window.location.search).get("digest");
    if (flag === "off") setDigestNote("아침 알림을 껐어요. 다시 받으려면 아래에서 켜면 돼요.");
    if (flag === "invalid") setDigestNote("링크가 맞지 않아요. 로그인한 뒤 아래에서 끌 수 있어요.");
    api
      .me()
      .then((m) => {
        setMe(m);
        if (m) {
          setEntryYear(m.entryYear ? String(m.entryYear) : "");
          setSemester(m.semesterManual && m.semester ? String(m.semester) : "");
          setMajor(m.major ?? "");
          setSecondMajor(m.secondMajor ?? "");
          const [s, g] = (m.homeRegion ?? "").split(" ");
          setSido(s ?? "");
          setSigungu(g ?? "");
          api.regions().then(setRegions).catch(() => setRegions([]));
        }
      })
      .catch((e) => {
        setMe(null);
        setError(e.message);
      });
  }, []);

  const requestLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.requestLogin(email);
      setSentTo(email);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const saveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setSaved(false);
    try {
      const updated = await api.updateProfile({
        entryYear: entryYear ? Number(entryYear) : null,
        semester: entryYear && semester ? Number(semester) : null,
        major,
        secondMajor,
        homeRegion: sido ? [sido, sigungu].filter(Boolean).join(" ") : "",
      });
      setMe(updated);
      setSemester(updated.semesterManual && updated.semester ? String(updated.semester) : "");
      setSaved(true);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const logout = async () => {
    await api.logout();
    setMe(null);
    setSaved(false);
  };

  const setDigest = async (enabled: boolean) => {
    setBusy(true);
    setError(null);
    try {
      setMe(await api.setDigest(enabled));
      setDigestNote(null);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const deleteAccount = async () => {
    setBusy(true);
    setError(null);
    try {
      await api.deleteAccount();
      setMe(null);
      setConfirmDelete(false);
      setDigestNote("계정과 저장한 정보를 모두 지웠어요.");
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <main id="main" className="narrow">
      <div className="page-head">
        <h1>설정</h1>
        <p className="sub">{me ? me.email : "로그인하면 마감을 저장하고, 내 학년·전공에 맞는 항목을 먼저 볼 수 있어요."}</p>
      </div>

      {error && (
        <div className="msg error" role="alert">
          {error}
        </div>
      )}

      {digestNote && (
        <div className="msg" role="status">
          {digestNote}
        </div>
      )}

      {me === undefined ? (
        <div className="empty">불러오는 중…</div>
      ) : !me ? (
        <section className="panel">
          <h2 className="panel-title">이메일로 로그인</h2>
          {sentTo ? (
            <div role="status">
              <p>
                <strong>{sentTo}</strong>로 로그인 링크를 보냈어요.
              </p>
              <p className="muted">15분 안에 메일의 링크를 누르면 로그인돼요. 한 번만 쓸 수 있어요.</p>
              <p style={{ marginTop: 14 }}>
                <button onClick={() => setSentTo(null)}>다른 이메일로 받기</button>
              </p>
            </div>
          ) : (
            <form onSubmit={requestLogin}>
              <p className="muted">비밀번호는 없어요. 이메일로 온 링크를 누르면 로그인돼요.</p>
              <label htmlFor="email">이메일</label>
              <div className="row fit">
                <input
                  id="email"
                  type="email"
                  inputMode="email"
                  autoComplete="email"
                  spellCheck={false}
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@catholic.ac.kr"
                />
                <button className="primary" type="submit" disabled={busy}>
                  {busy ? "보내는 중…" : "링크 받기"}
                </button>
              </div>
            </form>
          )}
        </section>
      ) : (
        <>
          <section className="panel">
            <h2 className="panel-title">내 정보</h2>
            <p className="muted">
              모두 선택이에요. 넣으면 내게 맞는 공지가 홈 위쪽으로 올라와요. 소득이나 성적은 받지 않아요.
            </p>
            <form onSubmit={saveProfile}>
              <div className="grid2">
                <div>
                  <label htmlFor="entryYear">입학년도</label>
                  <input
                    id="entryYear"
                    type="number"
                    inputMode="numeric"
                    min={1990}
                    max={new Date().getFullYear()}
                    value={entryYear}
                    onChange={(e) => {
                      setEntryYear(e.target.value);
                      setSaved(false);
                    }}
                    placeholder="예: 2023"
                  />
                </div>
                <div>
                  <label htmlFor="semester">이번 학기</label>
                  <select
                    id="semester"
                    value={semester}
                    disabled={!entryYear}
                    onChange={(e) => {
                      setSemester(e.target.value);
                      setSaved(false);
                    }}
                  >
                    <option value="">
                      {me.autoSemester && Number(entryYear) === me.entryYear ? `자동 (${semesterName(me.autoSemester)})` : "자동"}
                    </option>
                    {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((s) => (
                      <option key={s} value={s}>
                        {semesterName(s)}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <label htmlFor="major">전공</label>
              <input
                id="major"
                value={major}
                onChange={(e) => {
                  setMajor(e.target.value);
                  setSaved(false);
                }}
                placeholder="예: 컴퓨터정보공학부"
              />
              <label htmlFor="secondMajor">복수·부전공</label>
              <input
                id="secondMajor"
                value={secondMajor}
                onChange={(e) => {
                  setSecondMajor(e.target.value);
                  setSaved(false);
                }}
                placeholder="없으면 비워 두세요"
              />
              <p className="hint">휴학이나 군 복무로 학기가 다르면 이번 학기를 골라 주세요. 3월과 9월에 한 학기씩 올라가요. "4학기 이상 이수" 같은 조건을 맞추는 데 써요.</p>

              <fieldset className="region">
                <legend>연고 지역</legend>
                <div className="grid2">
                  <div>
                    <label htmlFor="sido">시·도</label>
                    <select
                      id="sido"
                      value={sido}
                      onChange={(e) => {
                        setSido(e.target.value);
                        setSigungu("");
                        setSaved(false);
                      }}
                    >
                      <option value="">고르지 않음</option>
                      {regions.map((r) => (
                        <option key={r.name} value={r.name}>
                          {r.name}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label htmlFor="sigungu">시·군·구</label>
                    <select
                      id="sigungu"
                      value={sigungu}
                      disabled={!sido || (regions.find((r) => r.name === sido)?.sigungu.length ?? 0) === 0}
                      onChange={(e) => {
                        setSigungu(e.target.value);
                        setSaved(false);
                      }}
                    >
                      <option value="">시·도 전체</option>
                      {(regions.find((r) => r.name === sido)?.sigungu ?? []).map((g) => (
                        <option key={g} value={g}>
                          {g}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>
                <p className="hint">
                  본인이나 부모님 주민등록 주소예요. 그 지역 재단·지자체 장학금이 &lsquo;내게 맞는 공지&rsquo;로 올라와요.
                </p>
              </fieldset>
              <div className="row" style={{ marginTop: 16 }}>
                <button className="primary" type="submit" disabled={busy}>
                  {busy ? "저장 중…" : "저장"}
                </button>
                {saved && (
                  <span className="badge ok" role="status">
                    저장했어요
                  </span>
                )}
              </div>
            </form>
          </section>

          <section className="panel">
            <h2 className="panel-title">아침 알림 메일</h2>
            <label className="switch">
              <input
                type="checkbox"
                checked={me.digestEnabled}
                disabled={busy}
                onChange={(e) => setDigest(e.target.checked)}
              />
              <span>매일 아침 8시, 보낼 것이 있는 날만 한 통</span>
            </label>
            <ul className="plain-list">
              <li>새로 올라온 공지 가운데 꼭 챙길 일정, 내게 맞는 공지, 지역·특정 대상이 아닌 장학·학사 공지</li>
              <li>저장한 마감 3일 전</li>
              <li>체크한 혜택 만료 7일 전</li>
            </ul>
            <p className="hint">같은 공지는 한 번만 보내요. 메일 맨 아래 링크로도 끌 수 있어요.</p>
          </section>

          <FeedbackPanel />

          <section className="panel">
            <div className="row between">
              <span className="sub">로그인 상태는 30일 동안 유지돼요.</span>
              <button onClick={logout}>로그아웃</button>
            </div>
          </section>

          <section className="panel danger-zone">
            <h2 className="panel-title">탈퇴</h2>
            <p className="muted">
              이메일, 입학년도, 학기, 전공, 연고 지역, 저장한 마감, 체크한 혜택, 알림 기록, 남긴 의견, 날짜 신고를 모두 지워요. 되돌릴 수
              없어요.
            </p>
            {confirmDelete ? (
              <div className="row" style={{ marginTop: 12 }}>
                <button className="danger" onClick={deleteAccount} disabled={busy}>
                  {busy ? "지우는 중…" : "정말 지우기"}
                </button>
                <button onClick={() => setConfirmDelete(false)} disabled={busy}>
                  취소
                </button>
              </div>
            ) : (
              <p style={{ marginTop: 12 }}>
                <button className="danger" onClick={() => setConfirmDelete(true)}>
                  탈퇴하고 내 정보 지우기
                </button>
              </p>
            )}
          </section>
          <p className="hint" style={{ marginTop: 16 }}>
            받는 정보와 보관 기간은 <Link href="/privacy/">개인정보 처리방침</Link>에 있어요.
          </p>
        </>
      )}
    </main>
  );
}

/** 3 → "2학년 1학기", 9 → "초과학기". 학기는 1학년 1학기부터 센다. */
function semesterName(s: number): string {
  return s >= 9 ? "초과학기" : `${Math.ceil(s / 2)}학년 ${s % 2 === 1 ? 1 : 2}학기`;
}

const FEEDBACK_MAX = 300;

/** "이건 왜 없나요?" 한 줄. 운영자만 읽고, 누가 썼는지는 검수 화면에 나오지 않는다. */
function FeedbackPanel() {
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!message.trim()) return;
    setBusy(true);
    setError(null);
    try {
      await api.leaveFeedback(message.trim());
      setMessage("");
      setSent(true);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="panel">
      <h2 className="panel-title">이건 왜 없나요?</h2>
      <p className="muted">찾던 공지나 혜택이 없으면 한 줄 남겨 주세요. 모아서 수집 범위를 정할 때 봐요.</p>
      <form onSubmit={submit}>
        <label htmlFor="feedback" className="sr-only">
          남길 말
        </label>
        <div className="row fit">
          <input
            id="feedback"
            value={message}
            maxLength={FEEDBACK_MAX}
            onChange={(e) => {
              setMessage(e.target.value);
              setSent(false);
            }}
            placeholder="예: 우리 학과 장학 공지도 보고 싶어요"
          />
          <button className="primary" type="submit" disabled={busy || !message.trim()}>
            {busy ? "보내는 중…" : "보내기"}
          </button>
        </div>
      </form>
      {sent && (
        <p className="hint" role="status">
          받았어요. 고마워요.
        </p>
      )}
      {error && (
        <p className="msg error" role="alert">
          {error}
        </p>
      )}
    </section>
  );
}
