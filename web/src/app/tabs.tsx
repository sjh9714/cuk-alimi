"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { api, type Me } from "@/lib/api";

const LINKS = [
  { href: "/", label: "일정" },
  { href: "/benefits/", label: "혜택" },
  { href: "/settings/", label: "설정" },
];

/** PC에서는 위쪽 머리글, 모바일에서는 아래쪽 탭. 같은 링크를 CSS로 나눠 보여준다. */
export function Header() {
  const path = usePathname();
  const me = useMe(path);
  const links = me?.admin ? [...LINKS, { href: "/admin/", label: "검수" }] : LINKS;
  return (
    <>
      <header className="site-header">
        <div className="inner">
          <Link href="/" className="brand">
            CUK 알리미
          </Link>
          <nav className="top-nav" aria-label="주 메뉴">
            {links.map((l) => (
              <Link key={l.href} href={l.href} className={path === l.href ? "active" : ""}>
                {l.label}
              </Link>
            ))}
          </nav>
          <span className="header-user">
            <Link href="/settings/">{me ? "내 계정" : "로그인"}</Link>
          </span>
        </div>
      </header>
      <nav className="tabs" aria-label="주 메뉴">
        {links.map((l) => (
          <Link key={l.href} href={l.href} className={path === l.href ? "active" : ""}>
            {l.label}
          </Link>
        ))}
      </nav>
    </>
  );
}

function useMe(path: string): Me | null {
  const [me, setMe] = useState<Me | null>(null);
  useEffect(() => {
    api.me().then(setMe).catch(() => setMe(null));
  }, [path]);
  return me;
}
