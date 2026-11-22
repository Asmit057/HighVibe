#!/usr/bin/env bash
# Installs and starts HighVibe on a fresh Ubuntu server (Oracle Cloud Always Free or any VPS).
# Run it again at any time to pull the latest code and redeploy.
#
#   curl -fsSL https://raw.githubusercontent.com/Asmit057/HighVibe/main/scripts/deploy.sh | bash
#
# Optional: PUBLIC_HOST=my.domain.com  GEMINI_API_KEY=...  REPO_URL=...  APP_DIR=...
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/Asmit057/HighVibe.git}"
APP_DIR="${APP_DIR:-$HOME/HighVibe}"
PORTS="80,5200:5210"

step() { printf '\n\033[1;35m==> %s\033[0m\n' "$1"; }

step "Installing Docker"
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker "$USER"
fi
sudo apt-get update -qq && sudo apt-get install -y -qq git openssl curl >/dev/null

step "Adding swap (keeps the Maven build safe on small machines)"
if [ -z "$(swapon --show)" ]; then
  sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile
  sudo mkswap /swapfile >/dev/null && sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

step "Opening ports $PORTS in the server firewall"
# Oracle's Ubuntu images reject everything except SSH; other clouds usually allow all.
if ! sudo iptables -C INPUT -p tcp -m multiport --dports "$PORTS" -j ACCEPT 2>/dev/null; then
  sudo iptables -I INPUT -p tcp -m multiport --dports "$PORTS" -j ACCEPT
  if command -v netfilter-persistent >/dev/null 2>&1; then sudo netfilter-persistent save >/dev/null; fi
fi

step "Fetching the code"
if [ -d "$APP_DIR/.git" ]; then
  git -C "$APP_DIR" pull --ff-only
else
  git clone "$REPO_URL" "$APP_DIR"
fi
cd "$APP_DIR"

step "Writing .env"
set_env() {  # set_env KEY VALUE: replace (or add) KEY=VALUE in .env
  if grep -qE "^#? *$1=" .env; then sed -i -E "s|^#? *$1=.*|$1=$2|" .env; else echo "$1=$2" >> .env; fi
}
if [ ! -f .env ]; then
  cp .env.example .env
  set_env JWT_SECRET_KEY "$(openssl rand -hex 32)"
  set_env DB_PASSWORD "$(openssl rand -hex 16)"
  set_env MINIO_SECRET_KEY "$(openssl rand -hex 16)"
fi
if [ -n "${PUBLIC_HOST:-}" ]; then
  set_env PUBLIC_HOST "$PUBLIC_HOST"
elif ! grep -qE '^PUBLIC_HOST=.+' .env; then
  set_env PUBLIC_HOST "$(curl -fsS https://api.ipify.org)"
fi
if [ -n "${GEMINI_API_KEY:-}" ]; then set_env GEMINI_API_KEY "$GEMINI_API_KEY"; fi
chmod 600 .env
HOST="$(grep -E '^PUBLIC_HOST=' .env | cut -d= -f2-)"

step "Building and starting HighVibe (the first build takes 10-15 minutes)"
sudo docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile apps up -d --build --remove-orphans

step "Waiting for the API gateway"
for _ in $(seq 1 90); do
  if curl -fsS "http://localhost/api/billing/plans" >/dev/null 2>&1; then
    printf '\n\033[1;32mHighVibe is live at http://%s\033[0m\n' "$HOST"
    grep -qE '^GEMINI_API_KEY=.+' .env || echo "No GEMINI_API_KEY yet: the demo generator answers. Add the key to $APP_DIR/.env and run this script again."
    exit 0
  fi
  sleep 10
done
echo "The gateway is not answering yet. Check: sudo docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile apps ps"
exit 1
