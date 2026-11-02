# 006. 배포

**상황.** 실제 사용자가 접속하려면 서버가 켜져 있어야 한다. 새 비용은 0원이다. 웹푸시는 HTTPS가 필요하다.

**선택지.** (1) Oracle Cloud Always Free VM 1대 + Caddy(HTTPS 자동) + DuckDNS 무료 서브도메인, 프론트는 Vercel 무료. (2) 집 맥 + Cloudflare Tunnel. (3) Fly.io·Koyeb 무료 티어.

**결정.** (1). VM 한 대에 Docker Compose로 app, postgres, caddy를 올린다. 화면(`web/out`)도 같은 Caddy가 내보내고 `/api`만 app으로 넘긴다. 도메인은 DuckDNS로 시작하고, 유료 도메인은 사용자가 생긴 뒤 따로 정한다.

**정정 (2026-10-26).** 처음엔 화면을 Vercel에 두려 했다. 화면과 API가 다른 origin이면 쿠키에 SameSite=None과 CORS가 필요해 로그인이 복잡해진다. 같은 origin으로 묶으면 그 둘이 사라진다. Vercel은 쓰지 않는다.

**이유.** 영구 무료이고 상주 프로세스(수집기, 발송기)를 돌릴 수 있다. (2)는 맥이 꺼지면 서비스가 꺼진다. (3)은 유휴 시 꺼지거나 메모리가 부족하다.
