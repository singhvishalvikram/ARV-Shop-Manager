#!/usr/bin/env bash
# Provisions a fresh Ubuntu VM to run the ARV Shop Manager backend behind
# Caddy (automatic HTTPS). Run this AS ROOT (or via sudo) on the server —
# see docs/operator/05-free-server-deployment.md for the full walkthrough
# and what to do before/after running this.
#
# Usage: sudo AUTH_SECRET="$(openssl rand -base64 36)" ./provision.sh
set -euo pipefail

REPO_URL="https://github.com/singhvishalvikram/ARV-Shop-Manager.git"
BRANCH="feat/mobile-suite"
APP_DIR="/opt/arv-shop-manager"
DATA_DIR="/data"
ENV_FILE="/etc/arv-backend.env"

if [ "$(id -u)" -ne 0 ]; then
    echo "Run this as root (sudo ./provision.sh)." >&2
    exit 1
fi

if [ -z "${AUTH_SECRET:-}" ]; then
    echo "Set AUTH_SECRET first, e.g.:" >&2
    echo "  sudo AUTH_SECRET=\"\$(openssl rand -base64 36)\" ./provision.sh" >&2
    exit 1
fi

echo "==> Installing system packages (python3, git, caddy)"
apt-get update -y
apt-get install -y python3-venv python3-pip git curl debian-keyring debian-archive-keyring apt-transport-https

if ! command -v caddy >/dev/null 2>&1; then
    curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' \
        | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
    curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
        > /etc/apt/sources.list.d/caddy-stable.list
    apt-get update -y
    apt-get install -y caddy
fi

echo "==> Creating dedicated app user"
id -u arvapp >/dev/null 2>&1 || useradd --system --create-home --shell /usr/sbin/nologin arvapp

echo "==> Fetching the app (branch: $BRANCH)"
if [ -d "$APP_DIR/.git" ]; then
    git -C "$APP_DIR" fetch origin "$BRANCH"
    git -C "$APP_DIR" checkout "$BRANCH"
    git -C "$APP_DIR" pull origin "$BRANCH"
else
    git clone --branch "$BRANCH" "$REPO_URL" "$APP_DIR"
fi
chown -R arvapp:arvapp "$APP_DIR"

echo "==> Python venv + dependencies"
cd "$APP_DIR/shop-manager/backend"
sudo -u arvapp python3 -m venv .venv
sudo -u arvapp .venv/bin/pip install --upgrade pip -q
sudo -u arvapp .venv/bin/pip install -q -r requirements.txt

echo "==> Persistent data directories"
mkdir -p "$DATA_DIR/images/items" "$DATA_DIR/backups"
chown -R arvapp:arvapp "$DATA_DIR"

echo "==> Detecting public IP for a free sslip.io hostname"
PUBLIC_IP="$(curl -4 -s ifconfig.me || curl -4 -s icanhazip.com)"
HOSTNAME_DASHED="$(echo "$PUBLIC_IP" | tr '.' '-')"
HOSTNAME="${HOSTNAME_DASHED}.sslip.io"
echo "    Public IP: $PUBLIC_IP"
echo "    Hostname:  $HOSTNAME"

echo "==> Writing $ENV_FILE (0600, owned by arvapp)"
cat > "$ENV_FILE" <<EOF
AUTH_SECRET=$AUTH_SECRET
SHOP_DB_PATH=$DATA_DIR/shop.db
SHOP_IMAGES_DIR=$DATA_DIR/images/items
ALLOWED_HOSTS=$HOSTNAME
SESSION_TTL_DAYS=30
EOF
chown arvapp:arvapp "$ENV_FILE"
chmod 600 "$ENV_FILE"

echo "==> Installing the systemd service"
cp "$APP_DIR/shop-manager/backend/ops/systemd/arv-backend.service" /etc/systemd/system/arv-backend.service
systemctl daemon-reload
systemctl enable arv-backend
systemctl restart arv-backend

echo "==> Writing the Caddyfile"
sed "s/YOUR-HOSTNAME-HERE/$HOSTNAME/" "$APP_DIR/shop-manager/backend/ops/Caddyfile" > /etc/caddy/Caddyfile
systemctl enable caddy
systemctl restart caddy

sleep 2
echo ""
echo "==> Local health check (loopback, bypasses Caddy/TLS):"
curl -s http://127.0.0.1:8000/api/v1/health || echo "  (backend not responding yet — check: journalctl -u arv-backend -n 50)"
echo ""
echo "==> Your API base URL is:"
echo "    https://$HOSTNAME"
echo ""
echo "Next: confirm 80/443 are reachable from the internet (cloud firewall + local"
echo "firewall — see docs/operator/05-free-server-deployment.md §4), then:"
echo "    curl https://$HOSTNAME/api/v1/health"
