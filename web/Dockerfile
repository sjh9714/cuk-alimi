# 화면. Next.js를 정적으로 내보내 Caddy 이미지에 담는다. Caddy가 /api 는 app 컨테이너로 넘긴다.
FROM node:22-alpine AS build
WORKDIR /web
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM caddy:2-alpine
LABEL org.opencontainers.image.source=https://github.com/sjh9714/cuk-alimi
COPY --from=build /web/out /srv
COPY Caddyfile /etc/caddy/Caddyfile
