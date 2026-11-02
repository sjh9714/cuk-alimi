# 001. 스택

**상황.** 사용자 10명 규모의 정보 서비스를 2주 안에 만들고, 만든 사람이 Java·Spring 백엔드 신입으로 지원한다.

**선택지.** (1) Java 21 + Spring Boot 3 + PostgreSQL + Next.js. (2) Next.js 하나로 풀스택(서버리스). (3) Python FastAPI + 정적 프론트.

**결정.** (1). 백엔드는 Spring Boot 3.4, DB는 PostgreSQL 16, 프론트는 Next.js PWA, 로컬과 서버 모두 Docker Compose.

**이유.** 지원 직무의 스택이고, 기존 저장소(concert-booking, realtime-chat)와 같은 틀이라 설명 부담이 없다. (2)는 빠르지만 수집 스케줄러와 푸시 발송처럼 오래 도는 작업이 서버리스에 맞지 않는다. (3)은 ETA에서 써 봤지만 지원 직무와 멀다.
