# DEPLOYMENT.md — Deploy the backend + ship APKs that run on any phone

> **Version:** 2026-07-02 · **Risk tier:** Commercial/Production
> Governing standards: `GUARDRAILS.md`, `pipeline_ops.md`, `CODING_STANDARDS.md`.
> This is the end-to-end guide: **deploy the one backend, then build the two apps to point
> at it, then distribute.** For the pure backend-ops details see
> [`../docs/operator/03-deployment.md`](../docs/operator/03-deployment.md) (referenced, not
> duplicated). For sideloading and Android-version support see
> [`DEVICE-INSTALL.md`](DEVICE-INSTALL.md).

---

## 0. The shape of the system (why the backend matters)

```
   Customer app  ─────► GitHub Pages static JSON ──► (WhatsApp checkout)   [no backend]
   Customer PWA  ─────►┐
   Admin app     ─────►┤  FastAPI backend (shop-manager/backend, /api/v1)  [REQUIRED for owner]
                       └► SQLite (shop.db) · argon2id auth · Bearer sessions
```

- The **customer app** needs **no backend** — it is self-contained.
- The **admin app and the owner/PWA experience** are **useless without the backend** — it
  holds the accounts, inventory, sales and dashboard. So: **yes, the backend is needed**,
  and it lives in this same repo/branch under [`../shop-manager/backend/`](../shop-manager/backend/).

---

## 1. Deploy the backend (one service, behind HTTPS)

The backend is one FastAPI app. Full operator detail is in
[`../docs/operator/03-deployment.md`](../docs/operator/03-deployment.md); the essentials:

### 1.1 Configure (never commit secrets)
```bash
cp .env.example .env
# REQUIRED — generate a strong secret; empty AUTH_SECRET fails loudly by design:
python -c "import secrets; print('AUTH_SECRET=' + secrets.token_urlsafe(48))" >> .env
# SHOP_DB_PATH=/data/shop.db   (persistent volume)
# SESSION_TTL_DAYS=30
```

### 1.2 Run
```bash
cd shop-manager/backend
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000       # behind a TLS proxy in prod
```

### 1.3 Put it behind HTTPS (required for the `prod` app + PWA camera + real data)
Terminate TLS at Caddy or nginx and forward to uvicorn. Caddy example (auto-TLS):
```
api.yourshop.com {
    reverse_proxy 127.0.0.1:8000
    header_up X-Forwarded-For {remote_host}   # the auth rate-limiter reads this
}
```
Now the API base URL for the apps is `https://api.yourshop.com`.

### 1.4 Three hosting tiers (pick for your stage)

| Stage | How | Reachable from |
|---|---|---|
| **Local LAN test** | run uvicorn on your PC; find your IP (`ipconfig`/`ifconfig`) | phones on the **same Wi-Fi** only, over `http://<PC-IP>:8000` |
| **First real shop** | one small VPS/container (Railway, Render, Fly.io, a $5 VM) + Caddy TLS + a domain | **anywhere**, over `https://` |
| **Scale (later)** | container + managed Postgres + object storage + Redis rate-limit (see PRODUCT-ROADMAP §1.A/1.F) | anywhere, multi-shop |

### 1.5 Seed / verify
```bash
export AUTH_SECRET=…                       # same as .env
uvicorn app.main:app --port 8000 &
curl -s localhost:8000/api/v1/health       # -> {"success":true,...}
# Demo owner already seeded in the default shop.db:  9999999999 / owner1234
```

---

## 2. Build the apps to point at that backend

The backend URL is compiled into the admin APK (`BuildConfig.API_BASE_URL`). **Match the
build flavor to where the backend lives.**

### 2.1 Customer app (standalone)
```bash
cd mobile/customer-app
gradle wrapper --gradle-version 8.7
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
./gradlew assembleProdRelease                       # signed; catalog URL baked in
# override catalog: -PcatalogBaseUrl="https://<your-pages-origin>"
```

### 2.2 Admin app — choose the URL that matches your hosting tier
```bash
cd mobile/admin-app
gradle wrapper --gradle-version 8.7
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties

# (a) LAN test on a real phone (same Wi-Fi as the PC running the backend):
./gradlew assembleDevDebug -PapiBaseUrl="http://192.168.1.5:8000"

# (b) Real, sellable build — points at your HTTPS domain (prod is HTTPS-only):
./gradlew assembleProdRelease \
  -PapiBaseUrl="https://api.yourshop.com" \
  -PARV_KEYSTORE_PATH="$HOME/.arv-keystore/arv-release.jks" \
  -PARV_KEYSTORE_PASS=… -PARV_KEY_ALIAS=… -PARV_KEY_PASS=…
```
Output APKs land under `app/build/outputs/apk/…`. Install per
[`DEVICE-INSTALL.md`](DEVICE-INSTALL.md) §3.

---

## 3. Release signing & Play Store (the real distribution path)

- **Keystore:** the current `~/.arv-keystore/arv-release.jks` is a **DEV keystore, local
  only** — it must be **rotated to a fresh production keystore** before Play Store, and that
  key stored in **CI secrets**, never on the server or in git (pipeline_ops; ADR-003).
- **App Bundle:** publish an **AAB** (`bundleProdRelease`), not the APK, so Play generates
  per-device variants.
- **Play Console:** create the app, upload to the **internal testing** track first, complete
  the **Data safety** form (admin app collects the owner's phone number; customer app
  collects nothing), add a privacy policy URL, then promote to production.
- Two separate listings: `com.arvshop.admin` (owner) and `com.arvshop.customer` (shopper).

## 4. Distribution options summary

| Audience | Best channel |
|---|---|
| Yourself / one pilot shop | **Sideload** the signed APK (DEVICE-INSTALL §3) |
| A handful of beta owners | Play **internal/closed testing** track (AAB) |
| Public | Play **production** + optional direct-APK download from your site |

## 5. Pre-deploy checklist (every release)

- [ ] Backend CI green: pytest (75), ruff, secret scan, white-label gate (pipeline_ops).
- [ ] `AUTH_SECRET` set from a secret manager; **no** secrets in `.env` committed.
- [ ] Backend reachable over **HTTPS**; `X-Forwarded-For` forwarded.
- [ ] Admin APK built with the **matching** `apiBaseUrl` for the target audience.
- [ ] Release APK/AAB signed with the **production** keystore (not the dev one).
- [ ] DB on a persistent volume with a **tested** backup/restore.
- [ ] Bump `versionCode`/`versionName` for a Play upload.

For where the product goes after it's deployed (multi-tenancy, billing, security
hardening), see [`PRODUCT-ROADMAP.md`](PRODUCT-ROADMAP.md) and `NEXT-STEPS.md`.
