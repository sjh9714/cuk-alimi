#!/usr/bin/env bash
# Oracle Always Free VM(Ubuntu 24.04, ARM) 첫 준비. ubuntu 사용자로 한 번만 실행한다.
#   curl -fsSL https://raw.githubusercontent.com/sjh9714/cuk-alimi/main/deploy/setup-vm.sh | bash
# 하는 일: Docker 설치, 80·443 방화벽 열기(오라클 이미지는 iptables가 막고 있다), 저장소 받기.
set -euo pipefail

REPO="${REPO:-https://github.com/sjh9714/cuk-alimi.git}"
DIR="${DIR:-$HOME/cuk-alimi}"

echo "== Docker"
if ! command -v docker >/dev/null 2>&1; then
  sudo apt-get update -y
  sudo apt-get install -y ca-certificates curl git
  sudo install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  sudo chmod a+r /etc/apt/keyrings/docker.gpg
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
  sudo apt-get update -y
  sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
  sudo usermod -aG docker "$USER"
fi

echo "== 스왑 2GB (메모리 1GB 서버에서 OOM 방지)"
if ! swapon --show | grep -q /swapfile; then
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile >/dev/null
  sudo swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

echo "== 방화벽 80, 443"
# 오라클 Ubuntu 이미지는 iptables INPUT에 REJECT 규칙이 있어 보안 목록을 열어도 막힌다.
sudo iptables -C INPUT -p tcp --dport 80 -j ACCEPT 2>/dev/null || sudo iptables -I INPUT 6 -p tcp --dport 80 -j ACCEPT
sudo iptables -C INPUT -p tcp --dport 443 -j ACCEPT 2>/dev/null || sudo iptables -I INPUT 6 -p tcp --dport 443 -j ACCEPT
sudo iptables -C INPUT -p udp --dport 443 -j ACCEPT 2>/dev/null || sudo iptables -I INPUT 6 -p udp --dport 443 -j ACCEPT
sudo apt-get install -y netfilter-persistent iptables-persistent >/dev/null 2>&1 || true
sudo netfilter-persistent save >/dev/null 2>&1 || true

echo "== 저장소"
if [ ! -d "$DIR/.git" ]; then
  git clone "$REPO" "$DIR"
fi
cd "$DIR"
if [ ! -f .env ]; then
  cp .env.prod.example .env
  echo "   $DIR/.env 를 채운 뒤 deploy/deploy.sh 를 실행하세요."
fi

echo "== 끝. docker 그룹 반영을 위해 한 번 로그아웃했다가 다시 들어오세요."
