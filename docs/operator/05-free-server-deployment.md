# 5. Free, Long-Lived Server Deployment (Oracle Cloud Always Free + sslip.io)

> **Goal:** get the backend on a real public server, over real HTTPS, for **$0/month,
> indefinitely** — not a trial that expires in weeks. Then rebuild + test the admin APK
> against it.

## 0. Why this specific combination

You asked for free **and** long-lasting. Those two requirements rule out more than they
first appear to:

| Option | Genuinely free forever? | Persistent storage for `shop.db`? | Verdict |
|---|---|---|---|
| Render free web service | Yes, no time limit | **No** — free tier has no persistent disk; the filesystem resets on every restart/deploy/sleep-wake | Your inventory data would vanish periodically. Rejected. |
| Railway / Fly.io | No — both now require a card and charge once small included allowances are exceeded | Fly includes some volume storage | Not reliably "free forever." Rejected. |
| PythonAnywhere free tier | Yes, no card | Yes (small quota) | FastAPI (ASGI) isn't natively supported on their free web-app tier (WSGI only); would need real rework. Rejected for now. |
| **Oracle Cloud "Always Free" VM** | **Yes — explicitly permanent, not a trial** | **Yes — real persistent block storage, a real Linux VPS** | **Recommended.** |
| Google Cloud "Always Free" e2-micro | Yes, permanent | Yes | Good backup option if Oracle signup/capacity gives you trouble |

**Honest trade-off:** Oracle (like GCP) asks for a card at signup for identity
verification. You are **not charged** as long as you stay within the Always Free resource
limits (this app is tiny and will stay well within them) — but you should know that
up front before starting. If that's a dealbreaker, say so and we'll set up Google Cloud's
e2-micro Always Free instead (same guide applies, different signup screens).

Since you have no domain, we use **[sslip.io](https://sslip.io)** — a free service that
turns your server's bare IP into a real, resolvable hostname (e.g. `203-0-113-10.sslip.io`
resolves to `203.0.113.10`), which lets Caddy get you a **real Let's Encrypt HTTPS
certificate** with no domain purchase.

---

## 1. Create the Oracle Cloud VM (your part — I have no access to your account)

1. Sign up at [oracle.com/cloud/free](https://www.oracle.com/cloud/free/) (card required
   for verification; Always Free resources are not billed).
2. Create a Compute Instance:
   - **Image:** Ubuntu 24.04 (or latest LTS)
   - **Shape:** Ampere A1 (ARM, Always Free — 4 OCPU/24GB is generous for this app) if
     available in your region; otherwise the AMD-based "VM.Standard.E2.1.Micro" Always
     Free shape works fine too, just smaller.
   - Attach/confirm a **public IP**.
   - Download the SSH key pair Oracle generates (or supply your own public key).
3. In the VM's **Virtual Cloud Network → Security List**, add **Ingress Rules** for:
   - TCP **22** (SSH) — usually already open
   - TCP **80** and **443** (HTTP/HTTPS) — **this is the #1 thing people forget**, and
     without it Caddy can never get a certificate or serve traffic.
4. Note the instance's **public IP address** — you'll need it next.

---

## 2. Connect and run the provisioning script

```bash
ssh -i /path/to/your-key.key ubuntu@<PUBLIC_IP>
```

Once connected, as root (Oracle's Ubuntu image uses `sudo`):

```bash
sudo su -
git clone --branch feat/mobile-suite https://github.com/singhvishalvikram/ARV-Shop-Manager.git /tmp/arv-clone-for-script
cd /tmp/arv-clone-for-script/shop-manager/backend/ops
chmod +x provision.sh backup.sh
AUTH_SECRET="$(openssl rand -base64 36)" ./provision.sh
```

**What `provision.sh` does** (all of it, so nothing is a surprise — read it before running
if you want): installs Python/git/Caddy, creates a dedicated non-root `arvapp` user, clones
the app to `/opt/arv-shop-manager`, creates a venv and installs dependencies, creates
`/data` for the database + images (persistent, survives redeploys), writes
`/etc/arv-backend.env` (mode `600` — the secret is never world-readable or committed
anywhere), installs and starts the `arv-backend` systemd service (auto-restarts on crash,
starts on boot), detects your public IP and derives the `sslip.io` hostname, and writes
`/etc/caddy/Caddyfile` pointing Caddy at that hostname with automatic HTTPS.

It prints your API base URL at the end, e.g.:
```
https://203-0-113-10.sslip.io
```

## 3. Verify it's actually live

```bash
curl https://203-0-113-10.sslip.io/api/v1/health
# {"success":true,"data":{"status":"ok","version":"1.0.0"},"error":null}
```

If this hangs or fails: it's almost always the Security List rule from step 1.3 (80/443
not open) — Caddy can't complete the Let's Encrypt HTTP-01 challenge without inbound 80.
Check logs on the server: `journalctl -u caddy -n 50` and `journalctl -u arv-backend -n 50`.

## 4. Seed the owner account (there is no seeded demo user on a fresh server)

```bash
curl -X POST https://203-0-113-10.sslip.io/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"phone":"<your real phone>","password":"<a real password>","name":"<your name>"}'
```
Or use the admin app's in-app **"Create a shop account"** screen once it's installed
(§6) — same effect, no `curl` needed.

## 5. Build the admin APK to point at your live server

```bash
cd mobile/admin-app
./gradlew assembleProdRelease \
  -PapiBaseUrl="https://203-0-113-10.sslip.io" \
  -PARV_KEYSTORE_PATH="$HOME/.arv-keystore/arv-release.jks" \
  -PARV_KEYSTORE_PASS=<your keystore password> -PARV_KEY_ALIAS=arv -PARV_KEY_PASS=<your key password>
```
This is the `prod` flavor (HTTPS-only, matches `usesCleartextTraffic=false`) — it will
**only** accept an `https://` URL, which your sslip.io + Caddy setup now provides. Output:
`app/build/outputs/apk/prod/release/app-prod-release.apk`, **signed with the release
keystore** — this one is safe to actually hand to someone, unlike the debug/loopback build
from `BUILD-AND-SHARE.md`.

If you'd rather not type the keystore password on the command line, omit the four
`ARV_*` properties and run `./gradlew assembleProdDebug -PapiBaseUrl="https://..."`
instead — debug-signed, no password needed, works identically for testing on your own
phone (just not for Play Store).

## 6. Install and test on your phone — works from anywhere, no cable, no Wi-Fi requirement

```bash
adb install -r app/build/outputs/apk/prod/release/app-prod-release.apk
```
or copy the `.apk` to the phone and tap it (`DEVICE-INSTALL.md` §3). Turn off Wi-Fi and
switch to mobile data — it should still log in and load the dashboard, since it's now
talking to a real public server, not your Mac.

## 7. Keep it running long-term

- **Auto-restart & boot-start:** already handled by the systemd unit
  (`systemctl status arv-backend`).
- **Backups:** `ops/backup.sh` is on the server at `/opt/arv-shop-manager/shop-manager/
  backend/ops/backup.sh`. Add a daily cron job:
  ```bash
  (crontab -l 2>/dev/null; echo "0 2 * * * SHOP_DB_DIR=/data BACKUP_DIR=/data/backups /opt/arv-shop-manager/shop-manager/backend/ops/backup.sh >> /var/log/arv-backup.log 2>&1") | crontab -
  ```
- **Updates:** `sudo -u arvapp git -C /opt/arv-shop-manager pull && systemctl restart arv-backend`
  (or re-run `provision.sh`, which is safe to re-run).
- **Monitoring:** `curl https://<hostname>/api/v1/health` from anywhere, anytime, is your
  cheapest uptime check — wire it into a free uptime monitor (e.g. UptimeRobot) if you want
  an alert when it goes down.

## 8. What this is — and isn't — a substitute for

This gets you a genuinely free, durable, HTTPS, single-shop production deployment — good
enough for a real pilot with one shop owner. It is **still single-tenant** (one `shop.db`,
one shop) and running on a **single small VM with no managed failover** — see
`mobile/PRODUCT-ROADMAP.md` for what's needed to sell to a second shop (Postgres +
multi-tenancy) or to harden further before real customer PII scales up.
