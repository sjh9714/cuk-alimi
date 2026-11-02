import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = { title: "없는 페이지" };

export default function NotFound() {
  return (
    <main id="main" className="narrow">
      <div className="page-head">
        <h1>없는 페이지예요</h1>
        <p className="sub">주소가 바뀌었거나 잘못 들어왔어요.</p>
      </div>
      <Link className="btn primary" href="/">
        일정으로 돌아가기
      </Link>
    </main>
  );
}
