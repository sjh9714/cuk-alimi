"use client";

import { useEffect, useMemo, useState } from "react";
import {
  ApiError,
  api,
  formatRange,
  type FeedbackEntry,
  type Item,
  type ItemEdit,
  type ReviewNotice,
  type ReviewQueue,
  type Scope,
} from "@/lib/api";
import { ChevronIcon, ExternalIcon, PlusIcon } from "../icons";

type Reason = ReviewNotice["reviewReason"];

const REASON_LABEL: Record<Reason, string> = {
  PARSED: "날짜 읽음",
  IMAGE_ONLY: "그림만",
  NO_DATE: "날짜 없음",
  PARSE_FAILED: "읽기 실패",
};

const REASON_HELP: Record<Reason, string> = {
  PARSED: "날짜를 읽었어요. 맞는지 확인하고 대상을 붙여 주세요.",
  IMAGE_ONLY: "본문이 그림이에요. 그림에서 읽은 날짜가 있으면 원문 그림과 맞는지 보고 검수 완료를 누르세요. 없으면 직접 넣어 주세요.",
  NO_DATE: "글자에서 날짜를 못 찾았어요. 마감이 없는 글이면 바로 검수 완료를 누르세요.",
  PARSE_FAILED: "페이지를 못 읽었어요. 원문을 열어 확인해 주세요.",
};

const REASON_TONE: Record<Reason, string> = {
  PARSED: "ok",
  IMAGE_ONLY: "warn",
  NO_DATE: "",
  PARSE_FAILED: "urgent",
};

const PRIORITY_LABEL = ["모든 재학생 대상", "일반 공지", "지역·특정 대상·취업"] as const;

const SCOPE_LABEL: Record<Scope, string> = {
  GENERAL: "일반",
  REGION: "지역 연고",
  GROUP: "특정 계층",
  FIELD: "특정 분야",
  RESULT: "결과 발표 (학생 화면에서 뺌)",
};

export default function Admin() {
  const [queue, setQueue] = useState<ReviewQueue | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [forbidden, setForbidden] = useState(false);
  const [filter, setFilter] = useState<Reason | "ALL">("ALL");
  const [selected, setSelected] = useState<number | null>(null);
  const [approving, setApproving] = useState<number | null>(null);

  const load = () =>
    api
      .review()
      .then((q) => {
        setQueue(q);
        setError(null);
        setSelected((cur) => (cur !== null && q.notices.some((n) => n.id === cur) ? cur : null));
      })
      .catch((e) => {
        if (e instanceof ApiError && (e.status === 401 || e.status === 403)) setForbidden(true);
        else setError(e.message);
      });

  useEffect(() => {
    load();
  }, []);

  const approve = async (id: number) => {
    setApproving(id);
    try {
      await api.markReviewed(id);
      await load();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setApproving(null);
    }
  };

  const notices = queue?.notices ?? [];
  const counts = useMemo(() => {
    const c: Record<string, number> = { ALL: notices.length };
    notices.forEach((n) => (c[n.reviewReason] = (c[n.reviewReason] ?? 0) + 1));
    return c;
  }, [notices]);

  const list = notices.filter((n) => filter === "ALL" || n.reviewReason === filter);
  const current = notices.find((n) => n.id === selected) ?? null;
  const closedTotal = queue ? queue.closed.PAST + queue.closed.RESULT + queue.closed.STALE : 0;

  if (forbidden) {
    return (
      <main id="main" className="narrow">
        <div className="page-head">
          <h1>검수</h1>
        </div>
        <div className="empty">관리자만 들어올 수 있어요.</div>
      </main>
    );
  }

  return (
    <main id="main">
      <div className="page-head">
        <h1>검수</h1>
        {queue && (
          <p className="sub">
            볼 공지 {notices.length}건. 위에서부터 보세요. 모든 재학생이 보는 공지가 먼저, 그림에만 날짜가 있어 학생 화면에
            날짜 없이 &lsquo;확인 중&rsquo;으로 나가는 공지가 그다음이에요.
            {closedTotal > 0 && (
              <>
                {" "}
                자동 정리 {closedTotal}건 (마감 지남 {queue.closed.PAST}, 결과 발표 {queue.closed.RESULT}, 날짜 없는 지난 안내{" "}
                {queue.closed.STALE}).
              </>
            )}
          </p>
        )}
      </div>
      {error && (
        <div className="msg error" role="alert">
          {error}
        </div>
      )}

      {queue && (
        <div className="segmented wrap" role="group" aria-label="검수 사유 필터">
          {(["ALL", "PARSED", "IMAGE_ONLY", "NO_DATE", "PARSE_FAILED"] as const).map((r) => (
            <button key={r} aria-pressed={filter === r} onClick={() => setFilter(r)}>
              {r === "ALL" ? "전체" : REASON_LABEL[r]} <span className="n">{counts[r] ?? 0}</span>
            </button>
          ))}
        </div>
      )}

      {queue === null ? (
        !error && <div className="empty">불러오는 중…</div>
      ) : notices.length === 0 ? (
        <div className="empty">검수할 공지가 없어요.</div>
      ) : (
        <div className={`review ${current ? "has-detail" : ""}`}>
          <div className="review-list" aria-label="검수할 공지">
            {[0, 1, 2].map((p) => {
              const group = list.filter((n) => n.priority === p);
              if (group.length === 0) return null;
              return (
                <section key={p} className="review-group" aria-labelledby={`prio-${p}`}>
                  <h2 id={`prio-${p}`} className="review-group-title">
                    {PRIORITY_LABEL[p]} <span className="count">{group.length}</span>
                  </h2>
                  <ul>
                    {group.map((n) => (
                      <li key={n.id}>
                        <button
                          className={`review-row ${n.id === selected ? "selected" : ""}`}
                          aria-current={n.id === selected}
                          onClick={() => setSelected(n.id)}
                        >
                          <span className="review-meta">
                            <span className={`reason ${REASON_TONE[n.reviewReason]}`}>{REASON_LABEL[n.reviewReason]}</span>
                            <span>{n.category}</span>
                            {n.audienceLabel && <span>{n.audienceLabel}</span>}
                            <span className="date">{n.postedOn}</span>
                          </span>
                          <span className="review-title">{n.shortTitle}</span>
                          {n.items.length > 0 && <span className="muted">날짜 항목 {n.items.length}개</span>}
                        </button>
                        {n.items.some((i) => i.fromImage) && (
                          <p className="quick">
                            <span>
                              그림에서 읽음{" "}
                              {n.items
                                .filter((i) => i.fromImage)
                                .map((i) => i.endsOn.slice(5).replace("-", "."))
                                .join(", ")}
                            </span>
                            <a href={n.url} target="_blank" rel="noreferrer">
                              원문과 맞춰 보기
                            </a>
                            <button onClick={() => approve(n.id)} disabled={approving === n.id}>
                              {approving === n.id ? "승인 중…" : "이 날짜로 승인"}
                            </button>
                          </p>
                        )}
                      </li>
                    ))}
                  </ul>
                </section>
              );
            })}
            {list.length === 0 && <p className="muted" style={{ padding: 16 }}>이 사유의 공지가 없어요.</p>}
          </div>

          <section className="review-detail" aria-label="선택한 공지">
            {current ? (
              <NoticeDetail
                key={current.id}
                notice={current}
                onBack={() => setSelected(null)}
                onChange={load}
                onError={setError}
              />
            ) : (
              <div className="empty">왼쪽에서 공지를 고르세요.</div>
            )}
          </section>
        </div>
      )}
      <FeedbackList />
    </main>
  );
}

/** 설정 화면의 "이건 왜 없나요?"로 들어온 말. 수집 범위(학과 게시판 등)를 정할 때 본다. */
function FeedbackList() {
  const [entries, setEntries] = useState<FeedbackEntry[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    api
      .feedback()
      .then(setEntries)
      .catch((e) => setError(e.message));
  }, []);
  return (
    <details className="block fold" style={{ marginTop: 24 }}>
      <summary>
        <span className="fold-head">
          <span className="fold-title">
            이건 왜 없나요? <span className="count">{entries ? `${entries.length}건` : ""}</span>
          </span>
          <span className="note">사용자가 남긴 한 줄. 최근 100건.</span>
        </span>
        <ChevronIcon />
      </summary>
      {error ? (
        <p className="msg error" role="alert">
          {error}
        </p>
      ) : entries === null ? (
        <p className="muted">불러오는 중…</p>
      ) : entries.length === 0 ? (
        <p className="muted">아직 남긴 말이 없어요.</p>
      ) : (
        <ul className="plain-list">
          {entries.map((f) => (
            <li key={f.id}>
              <span className="muted">{f.createdAt.slice(0, 10)}</span> {f.message}
            </li>
          ))}
        </ul>
      )}
    </details>
  );
}

function NoticeDetail({
  notice,
  onBack,
  onChange,
  onError,
}: {
  notice: ReviewNotice;
  onBack: () => void;
  onChange: () => void;
  onError: (m: string) => void;
}) {
  const [adding, setAdding] = useState(false);
  const [busy, setBusy] = useState(false);
  const done = async () => {
    setBusy(true);
    try {
      await api.markReviewed(notice.id);
      onChange();
    } catch (e) {
      onError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="panel">
      <button className="ghost back" onClick={onBack}>
        <ChevronIcon dir="left" /> 목록
      </button>
      <p className="review-meta">
        <span className={`reason ${REASON_TONE[notice.reviewReason]}`}>{REASON_LABEL[notice.reviewReason]}</span>
        <span>{notice.category}</span>
        <span className="date">{notice.postedOn} 게시 · 글 번호 {notice.articleNo}</span>
      </p>
      <h2 className="detail-title">{notice.title}</h2>
      <p className="sub">{REASON_HELP[notice.reviewReason]}</p>
      <p style={{ marginTop: 12 }}>
        <a className="btn" href={notice.url} target="_blank" rel="noreferrer">
          학교 원문 열기 <ExternalIcon size={14} />
        </a>
      </p>
      {notice.attachments && <p className="muted" style={{ marginTop: 10 }}>첨부: {notice.attachments.split("\n").join(", ")}</p>}

      <AudienceEditor notice={notice} onChange={onChange} onError={onError} />

      <h3 className="detail-section">
        날짜 항목 <span className="count">{notice.items.length}</span>
      </h3>
      {notice.items.length === 0 && !adding && <p className="muted">아직 날짜 항목이 없어요. 학생 화면에 나가지 않아요.</p>}
      {notice.items.map((item) => (
        <ItemEditor key={item.id} item={item} onChange={onChange} onError={onError} />
      ))}
      {adding && (
        <ItemEditor
          item={null}
          noticeId={notice.id}
          onChange={() => {
            setAdding(false);
            onChange();
          }}
          onError={onError}
          onCancel={() => setAdding(false)}
        />
      )}

      <div className="row detail-actions">
        {!adding && (
          <button onClick={() => setAdding(true)}>
            <PlusIcon /> 날짜 항목 추가
          </button>
        )}
        <button className="primary" onClick={done} disabled={busy} style={{ marginLeft: "auto" }}>
          {busy ? "처리 중…" : "검수 완료"}
        </button>
      </div>
    </div>
  );
}

/** 대상 범위. 규칙이 정한 값을 보여주고, 틀렸으면 운영자가 고친다. 비우면 다시 규칙을 따른다. */
function AudienceEditor({
  notice,
  onChange,
  onError,
}: {
  notice: ReviewNotice;
  onChange: () => void;
  onError: (m: string) => void;
}) {
  const [scope, setScope] = useState<Scope | "">(notice.audienceOverridden ? notice.audienceScope : "");
  const [label, setLabel] = useState(notice.audienceOverridden ? notice.audienceLabel ?? "" : "");
  const [busy, setBusy] = useState(false);
  const id = `aud-${notice.id}`;
  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      await api.editAudience(notice.id, scope === "" ? null : scope, label);
      onChange();
    } catch (err) {
      onError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <form className="audience" onSubmit={save}>
      <h3 className="detail-section">대상</h3>
      <p>
        <strong>{SCOPE_LABEL[notice.audienceScope]}</strong>
        {notice.audienceLabel && ` · ${notice.audienceLabel}`}
        <span className="muted"> ({notice.audienceOverridden ? "운영자가 고침" : "제목·본문 규칙"})</span>
      </p>
      {notice.audienceExcerpt ? (
        <p className="excerpt">
          <span className="excerpt-label">원문 발췌</span>
          {notice.audienceExcerpt}
        </p>
      ) : (
        <p className="muted">본문에서 지원 자격 문단을 못 찾았어요. 학생 화면에는 발췌 없이 나가요.</p>
      )}
      <div className="grid2">
        <div>
          <label htmlFor={`${id}-scope`}>범위 고치기</label>
          <select id={`${id}-scope`} value={scope} onChange={(e) => setScope(e.target.value as Scope | "")}>
            <option value="">규칙에 맡김</option>
            {(Object.keys(SCOPE_LABEL) as Scope[]).map((s) => (
              <option key={s} value={s}>
                {SCOPE_LABEL[s]}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor={`${id}-label`}>짧은 설명 (선택)</label>
          <input
            id={`${id}-label`}
            value={label}
            onChange={(e) => setLabel(e.target.value)}
            placeholder="예: 제1전공생"
            disabled={scope === ""}
          />
        </div>
      </div>
      <p style={{ marginTop: 10 }}>
        <button type="submit" disabled={busy}>
          {busy ? "저장 중…" : "대상 저장"}
        </button>
      </p>
    </form>
  );
}

function ItemEditor({
  item,
  noticeId,
  onChange,
  onError,
  onCancel,
}: {
  item: Item | null;
  noticeId?: number;
  onChange: () => void;
  onError: (m: string) => void;
  onCancel?: () => void;
}) {
  const [open, setOpen] = useState(item === null);
  const [busy, setBusy] = useState(false);
  const [edit, setEdit] = useState<ItemEdit>({
    title: item?.title ?? "",
    startsOn: item?.startsOn ?? null,
    endsOn: item?.endsOn ?? "",
    startsAt: item?.startsAt?.slice(0, 5) ?? null,
    endsAt: item?.endsAt?.slice(0, 5) ?? null,
    targetGrade: item?.targetGrade ?? null,
    targetDept: item?.targetDept ?? null,
    targetNote: item?.targetNote ?? null,
  });
  const set = (k: keyof ItemEdit, v: string) => setEdit({ ...edit, [k]: v === "" ? null : v });
  const idp = `item-${item?.id ?? "new"}`;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      if (item) await api.editItem(item.id, edit);
      else await api.addItem(noticeId!, edit);
      setOpen(false);
      onChange();
    } catch (err) {
      onError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };
  const remove = async () => {
    if (!item) return;
    setBusy(true);
    try {
      await api.deleteItem(item.id);
      onChange();
    } catch (err) {
      onError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  if (!open && item) {
    return (
      <div className="item-row">
        <div>
          <div className="row" style={{ gap: 6 }}>
            {item.reviewed ? (
              <span className="badge ok">확인됨</span>
            ) : item.fromImage ? (
              <span className="badge warn">그림에서 읽음</span>
            ) : (
              <span className="badge">자동</span>
            )}
            <strong>{item.title}</strong>
          </div>
          <p className="muted" style={{ marginTop: 4 }}>
            {formatRange(item)}
            {item.targetGrade &&
              ` · ${item.targetGrade === "ALL" ? "전체 학년" : `${item.targetGrade}학년`}${
                item.targetDept && item.targetDept !== "ALL" ? ` · ${item.targetDept}` : ""
              }`}
            {!item.targetGrade && " · 대상 미정"}
          </p>
        </div>
        <button onClick={() => setOpen(true)}>고치기</button>
      </div>
    );
  }

  return (
    <form onSubmit={submit} className="item-form">
      <label htmlFor={`${idp}-title`}>제목</label>
      <input id={`${idp}-title`} value={edit.title} onChange={(e) => set("title", e.target.value)} required />
      <div className="grid2">
        <div>
          <label htmlFor={`${idp}-so`}>시작일 (선택)</label>
          <input id={`${idp}-so`} type="date" value={edit.startsOn ?? ""} onChange={(e) => set("startsOn", e.target.value)} />
        </div>
        <div>
          <label htmlFor={`${idp}-eo`}>마감일</label>
          <input id={`${idp}-eo`} type="date" value={edit.endsOn} onChange={(e) => set("endsOn", e.target.value)} required />
        </div>
        <div>
          <label htmlFor={`${idp}-sa`}>시작 시각 (선택)</label>
          <input id={`${idp}-sa`} type="time" value={edit.startsAt ?? ""} onChange={(e) => set("startsAt", e.target.value)} />
        </div>
        <div>
          <label htmlFor={`${idp}-ea`}>마감 시각 (선택)</label>
          <input id={`${idp}-ea`} type="time" value={edit.endsAt ?? ""} onChange={(e) => set("endsAt", e.target.value)} />
        </div>
        <div>
          <label htmlFor={`${idp}-g`}>대상 학년</label>
          <select id={`${idp}-g`} value={edit.targetGrade ?? ""} onChange={(e) => set("targetGrade", e.target.value)}>
            <option value="">모름</option>
            <option value="ALL">전체</option>
            <option value="1">1학년</option>
            <option value="2">2학년</option>
            <option value="3">3학년</option>
            <option value="4">4학년</option>
          </select>
        </div>
        <div>
          <label htmlFor={`${idp}-d`}>대상 학과</label>
          <input
            id={`${idp}-d`}
            value={edit.targetDept ?? ""}
            onChange={(e) => set("targetDept", e.target.value)}
            placeholder="전체면 ALL"
          />
        </div>
      </div>
      <label htmlFor={`${idp}-n`}>조건 한 줄 (선택)</label>
      <input
        id={`${idp}-n`}
        value={edit.targetNote ?? ""}
        onChange={(e) => set("targetNote", e.target.value)}
        placeholder="예: 소득 8구간 이하, 직전 학기 3.0 이상"
      />
      <div className="row" style={{ marginTop: 14 }}>
        <button className="primary" type="submit" disabled={busy}>
          {item ? "저장하고 확인됨으로" : "추가"}
        </button>
        <button
          type="button"
          onClick={() => {
            setOpen(false);
            onCancel?.();
          }}
        >
          닫기
        </button>
        {item && (
          <button type="button" className="ghost danger" onClick={remove} disabled={busy} style={{ marginLeft: "auto" }}>
            항목 지우기
          </button>
        )}
      </div>
    </form>
  );
}
