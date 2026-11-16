import type { Metadata, Viewport } from "next";
import "./pretendard.css";
import "./globals.css";
import { Header } from "./tabs";

export const metadata: Metadata = {
  title: { default: "CUK 알리미", template: "%s | CUK 알리미" },
  description: "가톨릭대 공지와 학사일정에서 신청 마감만 뽑아 날짜순으로 보여 드려요.",
  manifest: "/manifest.webmanifest",
  icons: { icon: "/icon.svg", apple: "/icon-180.png" },
  appleWebApp: { capable: true, title: "CUK 알리미", statusBarStyle: "default" },
};

export const viewport: Viewport = {
  themeColor: "#ffffff",
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
            <span>학교 홈페이지의 공개 공지를 옮긴 것이고, 학교와 관계없는 서비스예요.</span>
          </div>
        </footer>
      </body>
    </html>
  );
}
