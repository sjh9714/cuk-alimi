"use client";

import { useEffect, useState } from "react";
import { api, type BacktestScore, type Quality } from "@/lib/api";

const ADR_URL = "https://github.com/sjh9714/cuk-alimi/blob/main/docs/decisions/011-extraction-quality.md";

const percent = (v: number) => `${(v * 100).toFixed(1)}%`;

function ScoreRow({ label, s }: { label: string; s: BacktestScore }) {
  return (
    <tr>
      <th scope="row">{label}</th>
      <td>{s.notices}건</td>
      <td>{percent(s.precision)}</td>
      <td>{percent(s.recall)}</td>
      <td>
        {s.exactNotices}/{s.notices}건
      </td>
    </tr>
  );
}

/** 마감을 얼마나 맞게 뽑는지 공개한다. 정답셋 백테스트는 코드와 함께 바뀌고, 운영 수치는 검수 기록에서 센다. */
export default function QualityPage() {
  const [q, setQ] = useState<Quality | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    api
      .quality()
      .then(setQ)
      .catch(() => setFailed(true));
  }, []);

  return (
    <main id="main" className="narrow prose">
      <div className="page-head">
        <h1>마감 정확도</h1>
        <p className="sub">
          공지에서 마감을 얼마나 맞게 뽑는지 숫자로 공개해요. 날짜가 틀리면 그 날짜를 믿고 신청을 놓칠 수 있어서요.
        </p>
      </div>

      {failed && (
        <p className="panel" role="alert">
          수치를 불러오지 못했어요. 잠시 뒤 다시 열어 주세요.
        </p>
      )}
      {!q && !failed && (
        <p className="panel muted" role="status">
          불러오는 중…
        </p>
      )}

      {q && (
        <>
          <div className="spread stats">
            <div className="cell">
              <strong>{percent(q.backtest.all.precision)}</strong>
              <span>보여 준 마감 가운데 맞은 비율</span>
            </div>
            <div className="cell">
              <strong>{percent(q.backtest.all.recall)}</strong>
              <span>있어야 할 마감 가운데 찾은 비율</span>
            </div>
            <div className="cell">
              <strong>
                {q.backtest.all.exactNotices}/{q.backtest.all.notices}
              </strong>
              <span>마감을 모두 맞게 뽑은 공지</span>
            </div>
          </div>

          <section className="panel">
            <h2>정답셋으로 잰 정확도</h2>
            <p>
              공지 {q.backtest.notices}건에 학생이 할 일의 마감 {q.backtest.deadlines}개를 정답으로 붙여 두고, 코드가 바뀔
              때마다 같은 공지에서 다시 뽑아 비교해요. 수치가 떨어지면 바뀐 코드는 반영되지 않아요. 마지막으로 잰 날은{" "}
              <span style={{ whiteSpace: "nowrap" }}>{q.backtest.measuredOn}</span>이에요.
            </p>
            <table className="facts">
              <thead>
                <tr>
                  <th scope="col">공지</th>
                  <th scope="col">수</th>
                  <th scope="col">정밀도</th>
                  <th scope="col">재현율</th>
                  <th scope="col">모두 맞은 공지</th>
                </tr>
              </thead>
              <tbody>
                <ScoreRow label="전체" s={q.backtest.all} />
                <ScoreRow label="글 공지" s={q.backtest.text} />
                <ScoreRow label="그림 공지" s={q.backtest.image} />
              </tbody>
            </table>
            <p className="muted">
              정밀도는 보여 준 마감 가운데 맞은 비율, 재현율은 있어야 할 마감 가운데 찾아낸 비율이에요. 정답은 뽑은 결과를
              보지 않고 공지 원문만 읽어 붙였어요. 그림 공지는 규칙을 고칠 때 함께 본 공지라 실제보다 높게 나올 수 있어요.
            </p>
          </section>

          <section className="panel">
            <h2>아직 못 고친 오류</h2>
            {q.backtest.remaining.length === 0 ? (
              <p>정답셋에서 남은 오류가 없어요.</p>
            ) : (
              <ul>
                {q.backtest.remaining.map((r) => (
                  <li key={r.type}>
                    {r.description}
                    {r.missed > 0 && ` (놓친 마감 ${r.missed}개)`}
                    {r.invented > 0 && ` (없는 마감 ${r.invented}개)`}
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="panel">
            <h2>운영 중 검수</h2>
            {q.review.judged + q.review.added === 0 ? (
              <p>
                아직 검수 기록이 없어요. 운영을 시작하면 자동으로 뽑은 날짜를 운영자가 그대로 승인했는지, 고쳤는지,
                지웠는지가 여기 쌓여요.
              </p>
            ) : (
              <table className="facts">
                <tbody>
                  <tr>
                    <th scope="row">운영자가 확인한 자동 날짜</th>
                    <td>{q.review.judged}개</td>
                  </tr>
                  <tr>
                    <th scope="row">그대로 승인</th>
                    <td>
                      {q.review.approvedAsIs}개
                      {q.review.judged > 0 && ` (${percent(q.review.approvedAsIs / q.review.judged)})`}
                    </td>
                  </tr>
                  <tr>
                    <th scope="row">고침</th>
                    <td>{q.review.edited}개</td>
                  </tr>
                  <tr>
                    <th scope="row">지움 (없는 마감)</th>
                    <td>{q.review.deleted}개</td>
                  </tr>
                  <tr>
                    <th scope="row">손으로 추가 (놓친 마감)</th>
                    <td>{q.review.added}개</td>
                  </tr>
                  <tr>
                    <th scope="row">그 가운데 그림에서 읽은 날짜</th>
                    <td>
                      {q.review.fromImageApprovedAsIs}/{q.review.fromImageJudged}개 그대로 승인
                    </td>
                  </tr>
                </tbody>
              </table>
            )}
          </section>

          <section className="panel">
            <h2>그림 공지 받아 적기</h2>
            <p>
              본문이 그림뿐인 공지는 AI(Claude Haiku)가 그림 속 글자를 두 번 따로 받아 적고, 날짜는 글 공지와 같은 규칙이
              뽑아요. 두 번의 날짜가 모두 같으면 바로 공개하고, 다르면 운영자가 원문과 맞춰 볼 때까지 공개하지 않아요. 공개한
              날짜가 원문과 다르면 학생이 신고할 수 있고, 신고가 오면 바로 내려요.
            </p>
            <table className="facts">
              <tbody>
                <tr>
                  <th scope="row">두 번 읽어 자동 공개한 날짜</th>
                  <td>{q.autoPublish.published}개</td>
                </tr>
                <tr>
                  <th scope="row">어긋나서 운영자를 기다리는 날짜</th>
                  <td>{q.autoPublish.waiting}개</td>
                </tr>
                <tr>
                  <th scope="row">학생 신고</th>
                  <td>{q.autoPublish.reports}건</td>
                </tr>
                <tr>
                  <th scope="row">받아 적은 횟수</th>
                  <td>{q.transcription.images}회</td>
                </tr>
                <tr>
                  <th scope="row">건너뛰거나 실패한 그림</th>
                  <td>{q.transcription.skipped}장</td>
                </tr>
                <tr>
                  <th scope="row">든 비용</th>
                  <td>${q.transcription.costUsd.toFixed(4)}</td>
                </tr>
              </tbody>
            </table>
          </section>

          {q.audienceBacktest && (
            <section className="panel">
              <h2>형광펜 정확도</h2>
              <p>
                홈에서 형광펜은 내 학년·학과·연고 지역에 맞는 일에 칠해요. 공지의 대상 조건은 AI(Claude Haiku)가 두 번 따로
                읽고, 두 번이 같을 때만 써요. 내 프로필과 맞는지는 AI가 아니라 코드가 비교하고, 프로필은 AI에 보내지 않아요.
                공지 {q.audienceBacktest.notices}건마다 {q.audienceBacktest.students}가지 학생 프로필로 정답과 비교했어요.
              </p>
              <table className="facts">
                <thead>
                  <tr>
                    <th scope="col">방법</th>
                    <td>칠한 것 중 맞은 비율</td>
                    <td>칠해야 할 것 중 칠한 비율</td>
                    <td>모든 학생에게 맞게 칠한 공지</td>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <th scope="row">AI가 읽은 조건</th>
                    <td>{percent(q.audienceBacktest.ai.precision)}</td>
                    <td>{percent(q.audienceBacktest.ai.recall)}</td>
                    <td>
                      {q.audienceBacktest.ai.exactNotices}/{q.audienceBacktest.notices}건
                    </td>
                  </tr>
                  <tr>
                    <th scope="row">이전 규칙(정규식)</th>
                    <td>{percent(q.audienceBacktest.regex.precision)}</td>
                    <td>{percent(q.audienceBacktest.regex.recall)}</td>
                    <td>
                      {q.audienceBacktest.regex.exactNotices}/{q.audienceBacktest.notices}건
                    </td>
                  </tr>
                </tbody>
              </table>
              <p className="muted">
                정답은 사람이 아니라 더 큰 모델({q.audienceBacktest.goldModel})이 같은 지시문으로 적었어요. 두 번 읽기가 같았던
                공지는 {q.audienceBacktest.agreed}/{q.audienceBacktest.notices}건이에요. {q.audienceBacktest.runSource}. 운영 중에는
                지금까지 공지 {q.audienceRead.notices}건을 읽었고 ${q.audienceRead.costUsd.toFixed(4)}이 들었어요.
              </p>
            </section>
          )}

          <p className="muted">
            재는 방법과 이유는{" "}
            <a href={ADR_URL} target="_blank" rel="noreferrer">
              결정 기록
            </a>
            에 있어요.
          </p>
        </>
      )}
    </main>
  );
}
