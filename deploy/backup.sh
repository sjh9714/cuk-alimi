#!/usr/bin/env bash
# 밤마다 DB를 덤프해 서버 밖으로 보낸다. 서버에서 cron으로 돌린다.
#   15 4 * * * ~/cuk-alimi/deploy/backup.sh >> ~/cuk-alimi-backup.log 2>&1
# BACKUP_DEST는 rclone 원격("gdrive:cuk-alimi")이나 scp 대상("user@host:/path")이다. 비우면 서버 안 ~/backups에만 남긴다.
# 복원: gunzip -c cuk-alimi-YYYYMMDD.sql.gz | docker compose -f docker-compose.prod.yml exec -T postgres psql -U cukalimi -d cukalimi
set -euo pipefail
cd "$(dirname "$0")/.."

stamp=$(date +%Y%m%d)
dir="$HOME/backups"
mkdir -p "$dir"
file="$dir/cuk-alimi-$stamp.sql.gz"
docker compose -f docker-compose.prod.yml exec -T postgres pg_dump -U cukalimi -d cukalimi --no-owner | gzip > "$file"
# .env에는 VAPID 키와 비밀번호가 있다. 잃으면 푸시 구독이 모두 끊기므로 함께 보관한다.
cp .env "$dir/env-$stamp" && chmod 600 "$dir/env-$stamp"

if [ -n "${BACKUP_DEST:-}" ]; then
  case "$BACKUP_DEST" in
    *:*/*|*@*) scp -q "$file" "$dir/env-$stamp" "$BACKUP_DEST/" ;;
    *) rclone copy "$file" "$BACKUP_DEST" && rclone copy "$dir/env-$stamp" "$BACKUP_DEST" ;;
  esac
fi
# 14일 지난 것은 지운다.
find "$dir" -name 'cuk-alimi-*.sql.gz' -mtime +14 -delete
find "$dir" -name 'env-*' -mtime +14 -delete
echo "backup ok $(du -h "$file" | cut -f1) $file"
