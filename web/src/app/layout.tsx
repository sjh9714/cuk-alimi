import type { Metadata, Viewport } from "next";
import "./pretendard.css";
import "./globals.css";
import { Header } from "./tabs";

export const metadata: Metadata = {
  title: "캠퍼스 알리미",
  description: "가톨릭대 학생에게 돈이 걸린 공지와 학생 혜택을, 출처와 확인일이 붙은 것만 알려 드립니다.",
  manifest: "/manifest.webmanifest",
  icons: { icon: "/icon.svg", apple: "/icon.svg" },
  appleWebApp: { capable: true, title: "캠퍼스 알리미", statusBarStyle: "default" },
};

export const viewport: Viewport = {
  themeColor: "#fcfcfa",
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body>
        <a className="skip" href="#main">
          본문으로 건너뛰기
        </a>
        <Header />
        {children}
        <footer className="site-footer">
          <div className="inner">
            <a href="/privacy/">개인정보 처리방침</a>
            <a href="/quality/">마감 정확도</a>
            <span>
              가톨릭대 학사일정과 장학·학사 공지에서 학생이 할 일만 골라 마감 순으로 보여 드려요. 공지는 학교 홈페이지의 공개 글을
              옮긴 것이며 이 서비스는 학교와 관계가 없어요.
            </span>
          </div>
        </footer>
      </body>
    </html>
  );
}
