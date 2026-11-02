"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";

/** 개인정보 처리방침. 받는 것이 적어서 한 화면에 다 적는다. 문의처는 보내는 메일 주소를 그대로 쓴다. */
export default function Privacy() {
  const [contact, setContact] = useState<string>("");
  useEffect(() => {
    api
      .meta()
      .then((m) => setContact(m.contactEmail))
      .catch(() => setContact(""));
  }, []);

  return (
    <main id="main" className="narrow prose">
      <div className="page-head">
        <h1>개인정보 처리방침</h1>
        <p className="sub">2026년 10월 28일부터 적용해요. CUK-Alimi는 가톨릭대 학생에게 학교 공지의 마감과 학생 혜택을 알려 주는 개인 운영 서비스예요.</p>
      </div>

      <section className="panel">
        <h2>받는 정보와 이유</h2>
        <table className="facts">
          <thead>
            <tr>
              <th scope="col">정보</th>
              <th scope="col">언제</th>
              <th scope="col">왜</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>이메일</td>
              <td>로그인할 때</td>
              <td>로그인 링크와 아침 알림 메일을 보내려고</td>
            </tr>
            <tr>
              <td>입학년도, 전공, 복수·부전공, 연고 지역</td>
              <td>설정에서 직접 넣을 때 (모두 선택)</td>
              <td>내게 맞는 공지를 위로 올리려고</td>
            </tr>
            <tr>
              <td>저장한 마감, 체크한 혜택</td>
              <td>누를 때</td>
              <td>마감 전에 알려 주려고</td>
            </tr>
            <tr>
              <td>보낸 알림 기록</td>
              <td>메일을 보낼 때</td>
              <td>같은 알림을 두 번 보내지 않으려고</td>
            </tr>
            <tr>
              <td>&lsquo;이건 왜 없나요?&rsquo;에 남긴 말</td>
              <td>설정에서 직접 보낼 때</td>
              <td>모을 공지와 혜택을 정하려고</td>
            </tr>
          </tbody>
        </table>
        <p>비밀번호, 소득, 성적, 학번, 이름은 받지 않아요. 로그인은 이메일로 온 일회용 링크로만 해요.</p>
      </section>

      <section className="panel">
        <h2>보관과 삭제</h2>
        <ul>
          <li>탈퇴할 때까지 보관해요. 설정 화면의 &lsquo;탈퇴하고 내 정보 지우기&rsquo;를 누르면 위 정보가 바로 모두 지워져요.</li>
          <li>로그인 링크는 15분, 로그인 상태는 30일 뒤에 저절로 끝나요.</li>
          <li>서버 로그에는 요청 시각과 주소가 남고, 보통 2주 안에 사라져요.</li>
        </ul>
      </section>

      <section className="panel">
        <h2>넘기지 않아요</h2>
        <ul>
          <li>다른 회사나 기관에 정보를 팔거나 넘기지 않아요.</li>
          <li>메일은 Google(Gmail)을 거쳐 보내요. Google은 메일을 전달하는 역할만 해요.</li>
          <li>서버는 Oracle Cloud(일본 오사카)에 있어요.</li>
          <li>광고나 방문 추적 도구를 쓰지 않아요.</li>
        </ul>
      </section>

      <section className="panel">
        <h2>문의</h2>
        <p>
          정보 열람, 정정, 삭제 요청이나 궁금한 점은{" "}
          {contact ? <a href={`mailto:${contact}`}>{contact}</a> : "운영자 메일"}로 보내 주세요. 보통 며칠 안에 답해요.
        </p>
        <p className="muted">
          학교 공지의 내용은 가톨릭대학교 홈페이지에 공개된 글을 그대로 옮긴 것이고, 이 서비스는 학교와 관계가 없어요.
        </p>
      </section>

      <p className="hint">
        <Link href="/settings/">설정으로 돌아가기</Link>
      </p>
    </main>
  );
}
