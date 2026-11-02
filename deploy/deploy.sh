#!/usr/bin/env bash
# 최신 이미지를 받아 다시 띄운다. 서버에서 실행. DB 데이터는 볼륨에 남는다.
# 이미지는 GitHub Actions가 main 푸시마다 빌드한다. 서버에서는 빌드하지 않는다.
#   ~/cuk-alimi/deploy/deploy.sh            최신(latest)
#   IMAGE_TAG=<커밋 SHA> ~/cuk-alimi/deploy/deploy.sh   특정 커밋으로 되돌리기
set -euo pipefail
cd "$(dirname "$0")/.."

git pull --ff-only
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
docker image prune -f >/dev/null

echo
docker compose -f docker-compose.prod.yml ps
echo
echo "로그: docker compose -f docker-compose.prod.yml logs -f app"
echo "첫 수집: docker compose -f docker-compose.prod.yml exec -e RUN_ONCE=all app java -jar /app/app.jar"
