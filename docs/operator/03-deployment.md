# 3. Deployment (one FastAPI service, two faces)

The whole product is **one FastAPI service**: owner app at `/`, catalog at `/catalog`,
API at `/api/v1`. Deploy that once behind HTTPS.

## 3.1 Environment

Copy `.env.example` → `.env` and set at least:

```dotenv
AUTH_SECRET=<python -c "import secrets;print(secrets.token_urlsafe(48))">   # REQUIRED
SHOP_DB_PATH=/data/shop.db            # persistent path (see 3.4)
SHOP_IMAGES_DIR=/data/images/items    # persistent path under the served static dir*
SESSION_TTL_DAYS=30
# Google (optional) — see 02-google-oauth-setup.md
# Catalog CORS (only if hosting the catalog cross-origin) — see 3.5
```

\* If you keep images on a separate volume, make sure that path is **served at
`/static/images/items`** (the default `SHOP_IMAGES_DIR` already sits under the static dir).
Otherwise set `IMAGE_URL_PREFIX` to match how it's served.

**Never commit `.env`.** `AUTH_SECRET` empty = sessions/OAuth won't work (that's intentional —
it fails loudly instead of using a hardcoded secret).

## 3.2 Run

```bash
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8080      # behind a TLS proxy
```

Use a process manager (systemd / container) and run **multiple workers** behind the proxy
only after reading 3.6 (state notes).

## 3.3 HTTPS (required)

Terminate TLS at a reverse proxy (Caddy/nginx) and forward to uvicorn. HTTPS is required for:
camera capture (`getUserMedia`), PWA install, the OAuth state cookie, and the eventual TWA.

- Forward the real client IP as **`X-Forwarded-For`** (the auth rate-limiter reads it).
- Caddy gives you automatic TLS with a one-line config; nginx + certbot also works.

## 3.4 Database

- SQLite (`SHOP_DB_PATH`) on a **persistent volume**; back it up regularly (copy the `.db`
  plus `-wal`/`-shm`, or use the existing backup flow). WAL mode is on.
- At real multi-shop scale, migrate to **Postgres + `shop_id` + Row-Level Security** (the
  planned target; deferred until demand — see CLAUDE.md §4). Don't shard SQLite per shop.

## 3.5 Catalog hosting — pick one

- **Same-origin (simplest):** serve the catalog from this service at `/catalog`. No CORS.
  The catalog already prefers the live API and falls back to its static JSON.
- **Cross-origin (e.g. GitHub Pages):** set `CORS_ALLOW_ORIGINS=https://<pages-origin>` and
  add `<meta name="api-base" content="https://YOUR_DOMAIN/api/v1">` to the catalog's
  `index.html` so it calls your API. Without these it falls back to the published static JSON
  (still works, just not live inventory).

## 3.6 Scale & state notes (so multi-worker is safe)

Two pieces hold **in-process state** today (fine for a single instance, must move to a shared
store before horizontal scale):

- **Auth rate-limiter** (`core/rate_limit.py`) — counts per process. Use Redis at scale.
- **Image storage** — local disk. Swap to object storage (S3/GCS); the `ImageStorage`
  interface is already in place, so call sites don't change.

## 3.7 Before each deploy

- CI must be green: tests, ruff, gitleaks, white-label gate. Triage any **pip-audit**
  findings (advisory job).
- Bump the asset/SW version if you changed front-end files (query `?v=N`, and `CACHE_VERSION`
  in `sw.js`) so clients pick up changes.

## 3.8 Mobile app (TWA) — later

Wrapping the owner PWA as an installable Android app (TWA) and Play Store publishing are
**Phases 10/15/16** — deferred until the web app is verified and a shop is committed. Key
prerequisites captured for then: a verified HTTPS origin + Digital Asset Links, and a signing
keystore built/stored in **cloud CI** (never on the server). See
[ADR-001](../architecture/adr-001-owner-twa-customer-pwa.md) /
[ADR-003](../architecture/adr-003-apk-builds-in-cloud-ci.md).
