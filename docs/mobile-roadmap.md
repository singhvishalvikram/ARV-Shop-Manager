# Mobile Roadmap — ARV Shop Manager

> **Governing rules:** Enterprise Coding Standards (Commercial Tier, all Pillars 0–7).
> Precedence: Enterprise Coding Standards > [`CLAUDE.md`](../CLAUDE.md) > `agent.md` > audit.
> This document is the execution tracker for the mobile strategy. Each phase is
> updated in-place as it is completed.

## Status legend

| Mark | Meaning |
|------|---------|
| ⬜ | Not started |
| 🟦 | In progress |
| ✅ | Done (with evidence) |
| ⏸️ | Deferred (gated on demand / external dependency) |

## Strategy (decided)

**One backend, two faces, two distributions.**

```
                       ┌──────────────────────────────┐
                       │   FastAPI  /api/v1  (done)    │
                       │  owner routes │ catalog routes │
                       └───────┬───────────────┬────────┘
            Bearer session     │               │   public, safe fields
                       ┌───────┴──────┐  ┌──────┴─────────┐
                       │  OWNER app   │  │ CUSTOMER catalog│
                       │  (PWA→TWA)   │  │     (PWA)       │
                       │  installable │  │  WhatsApp link  │
                       │  one APK,    │  │  white-label,   │
                       │  white-label │  │  no app store   │
                       └──────────────┘  └────────────────┘
```

**Non-negotiables:** one shell per audience white-labeled from `settings` (never
per-shop APKs); customer catalog is a link, not a store app; owner and customer are
never fused into one downloadable app; APKs build in the **cloud** (the host box is
x86/ARM-blocked).

## Target front-end folder structure (refactor goal)

```
frontend/
├── shared/
│   ├── api/                 # the ONLY place fetch() lives (Pillar 3.3)
│   │   ├── http-client.js   # base fetch wrapper: envelope unwrap, errors, CSRF
│   │   ├── auth-service.js  # login/session/token
│   │   ├── items-service.js · sales-service.js · catalog-service.js · cart-service.js
│   ├── config/
│   │   ├── runtime-config.js # shop config from /catalog/settings → branding
│   │   └── env.js            # API base URL etc. (no secrets)
│   ├── pwa/
│   │   ├── service-worker.js # one versioned SW strategy
│   │   └── manifest-template.js # dynamic, settings-driven manifest
│   └── ui/                   # reusable components (atomic rule, Pillar 3.1)
├── owner-app/               # the installable owner PWA (→ TWA)
│   ├── pages/ (dashboard, inventory, sales, camera, settings)
│   ├── state/  └── index.html
└── customer-catalog/        # the white-label catalog PWA
    ├── pages/ (catalog, product, cart) └── index.html
android/                     # generated TWA project (Bubblewrap) — built in CI
```

**Clean-architecture flow (client):** UI → state → service layer (`shared/api`) →
HTTP client → `/api/v1`. No component calls `fetch` directly. Branding/theme flow
one-way from `runtime-config` (settings) so the same build white-labels any shop.

---

## Phases

### ✅ Phase 1 — Mobile Architecture Blueprint & ADRs
- **Goal:** Lock the two-app / two-distribution model in writing before building.
- **Tasks:** ADRs (§5.4): "owner=TWA, customer=PWA-link", "one white-label shell, no
  per-shop APK", "APK builds in cloud CI". Decide hosting domains (owner app URL,
  `{slug}` catalog URL), Play Store account ownership, tier confirmation (Commercial).
- **Deliverables:** `/docs/architecture/adr-001..003`, domain/DNS plan.
- **Standards:** §5.4, GUARDRAILS risk-tier declaration.
- **Exit:** ADRs approved; domains reserved; no open architectural questions.
- **✅ Done (2026-06-29):** [adr-001](architecture/adr-001-owner-twa-customer-pwa.md),
  [adr-002](architecture/adr-002-one-white-label-shell-no-per-shop-apk.md),
  [adr-003](architecture/adr-003-apk-builds-in-cloud-ci.md) written and accepted.
  **Owner action items (not engineering-blocking for Phases 2–13):** register hosting
  domain + catalog slug scheme, and create/own the Play Store account ($25) — these are
  external procurement, tracked for Phase 10/16.

### 🟦 Phase 2 — Front-End Clean-Architecture Refactor (API service layer)
- **Goal:** Kill inline `fetch`/hardcoded endpoints; route everything through a service
  layer pointed at `/api/v1`.
- **Tasks:** Build `shared/api/http-client.js` (envelope unwrap, error→message, abort
  controllers), per-resource services; replace direct calls in both front-ends; delete
  dead Node/Flask endpoint references.
- **Deliverables:** `shared/api/*`, refactored `index.html`/JS for both faces.
- **Standards:** §3.3 (API isolation, abort), §4.2 (envelope), §1.2 (naming).
- **Exit:** Zero `fetch(`/`axios` calls outside `shared/api`; both faces load against
  `/api/v1`.
- **🟦 In progress (2026-06-29):**
  - ✅ Service layer built at
    [`shop-manager/backend/static/shared/`](../shop-manager/backend/static/shared/):
    `env.js`, `http-client.js` (bearer + envelope unwrap + `ApiError` + `AbortSignal`),
    and services `auth`, `items`, `sales`, `dashboard`, `catalog`, `cart`, `settings`,
    plus a directory README documenting the no-direct-`fetch` rule.
  - ✅ **Repoint owner `static/js/app.js` — DONE via Phase 6** (was blocked on auth; now
    served same-origin with real login). Original blocker analysis retained below:
    - Owner `/api/v1` routes require a Bearer session token; the owner UI has **no login**
      today (client PIN only) → naive repoint 401s everything. **Depends on Phase 6.**
    - ~~`/api/v1/dashboard` omits `total_stock_mrp` and `recent_items` (the low-stock list
      source) → would regress the dashboard.~~ ✅ **Fixed 2026-06-29:** v1 dashboard now
      returns `total_stock_mrp` + `recent_items` (low-stock-first, only `{id,name,quantity}`);
      covered by `tests/test_dashboard_api.py` (40 passed, ruff clean).
    - `/api/v1/items` does **not** persist `image_base64` yet (object-storage migration
      deferred) → camera captures wouldn't save. **Depends on Phase 8.**
    - Compatible already: sale payload `{item_id,quantity,price,description}`, items
      list/create shapes.
  - ⛔ **Repoint catalog `customer-view/site/index.html` — partially blocked.** Product/
    settings **reads** can move to public `/api/v1/catalog/*`, but the catalog is served
    cross-origin (GitHub Pages) so it needs CORS + absolute API base (Phase 11/12), and
    the legacy `/api/auth/guest` guest-cart endpoint has **no v1 equivalent** → guest cart
    depends on a guest-session decision. **Depends on Phase 11.**
  - ➡️ **Re-sequencing decision (recorded):** the owner cutover is pulled to sit with
    Phase 6 (auth) + a small Phase-6-prep backend dashboard-parity fix; image persistence
    rides Phase 8. The service layer stands ready so those phases are pure wiring.

### ✅ Phase 3 — Config-Driven White-Label Branding
- **Goal:** Remove hardcoded "ARV ENTERPRISES"/colors; brand from `settings`.
- **Tasks:** `runtime-config.js` loads `/api/v1/catalog/settings` (app_title,
  theme_color, currency, whatsapp_number, logo); apply to DOM/theme/title; CI grep-gate
  forbidding the literal "ARV" in tenant-facing output.
- **Deliverables:** config layer + theming; CI check.
- **Standards:** CLAUDE.md §4 Phase 5; §3.4 (no hardcoded colors); §3.9 (no secrets).
- **Exit:** Changing a shop's `settings` changes the app's name/theme with no rebuild.
- **✅ Done (2026-06-29):** `static/shared/config/runtime-config.js` loads public
  `/catalog/settings` and applies `app_title`/`app_subtitle`/`theme_color` to the document
  (title, theme-color meta, `--brand-color` var, `data-brand-title` elements) with generic
  fallbacks; wired into the owner app pre-login via the `window.Branding` bridge. API title
  de-branded to "Shop Manager API" (env-overridable). **CI `white-label-gate`** fails the
  build on any hardcoded "ARV" in front-end assets. Test asserts the public brand keys.
  **46 passed, ruff clean, gate clean.** (CSS theming via `--brand-color` is a follow-up;
  the var + PWA theme-color + titles are config-driven now.)

### ✅ Phase 4 — PWA Manifest & Icon Pipeline
- **Goal:** Fix broken installability (empty `icons: []`) and make manifests dynamic.
- **Tasks:** Generate real PNG icon set (192/512 + maskable) per shop from logo; serve a
  per-shop dynamic manifest; consolidate the duplicate git-pages manifest.
- **Deliverables:** icon-generation step, dynamic manifest endpoint/template.
- **Standards:** §3.x PWA; white-label.
- **Exit:** Lighthouse "Installable" passes for both faces; no empty icon arrays.
- **✅ Done (2026-06-29):**
  - Generated real **PNG icons** (192/512, generic/unbranded) for owner + catalog faces
    (`static/icons/`, `customer-view/site/icons/`) — no more SVG-data-URI-only / empty arrays.
  - Owner manifest is now **dynamic from settings** (`app/manifest.py` + `/manifest.json`
    route: name/theme from `settings`, PNG icons incl. `maskable`). Static owner
    `manifest.json` deleted (one source of truth).
  - Catalog manifests (`customer-view/site`, `git-pages`) **de-branded** (generic name +
    real icons); static `<title>`s genericized (JS sets the real title from settings).
  - **Fixed the Phase-3 white-label gate** (it was passing locally only due to a wrong CWD;
    it would have been **red in CI**): now scoped to source and excludes runtime `*/data/`
    (a shop's configured name is legitimate tenant data, not hardcoded brand) and `/icons/`.
  - Tests: `test_manifest.py` (defaults, branding, endpoint, icons served). **61 passed,
    ruff clean, gate green from repo root.**

### ✅ Phase 5 — Unified Service-Worker Strategy
- **Goal:** One SW approach (today there are 2–3 inconsistent ones).
- **Tasks:** Single versioned `service-worker.js` (cache-first shell, network-first
  API/data), explicit version bump + update-on-reload flow, offline fallback; scope per
  face.
- **Deliverables:** `shared/pwa/service-worker.js`, update lifecycle.
- **Standards:** GUARDRAILS resilience (offline), §3.9 loading/empty/error states.
- **Exit:** Offline load works; new deploy reliably updates clients; no stale-cache lock.
- **✅ Done (2026-06-29):** One canonical SW strategy applied to both faces (owner
  `static/js/sw.js`, catalog `git-pages/sw.js`), versioned (`v5`):
    - **/api/ is never cached** (was: everything cached — a per-session staleness/leak
      risk); navigations are network-first → cache → offline page; static assets use
      stale-while-revalidate.
    - **Fixed a real scope bug:** the owner SW was registered at `/static/js/sw.js`, scoping
      it to `/static/js/` so it never controlled the app. Now served at **root `/sw.js`**
      (FastAPI route, `Service-Worker-Allowed: /`) and registered with `scope:'/'`.
    - Update flow hardened (single controllerchange reload, no reload loop).
  - Tests: `test_service_worker.py` (root serve + scope header + API-skip). **63 passed,
    ruff clean.**
  - Note: `customer-view/site` has no SW registration yet — added with the catalog work
    (Phase 11/12).

### ✅ Phase 6 — Owner PWA Consolidation  *(backend + frontend cutover done; browser-verify + Google creds pending)*
- **Goal:** Make the owner manager the single installable owner app on `/api/v1`.
- **Tasks:** Wire login/session (Bearer), dashboard/inventory/sales/settings via services;
  role enforcement; remove legacy Flask UI coupling.
- **Deliverables:** `owner-app/` complete against `/api/v1`.
- **Standards:** §2.x layering, §3.1 component anatomy, §3.9 loading states.
- **Exit:** Full owner workflow works as a PWA; legacy Flask UI unused.
- **🟦 In progress (2026-06-29) — BACKEND DONE:**
  - ✅ **Auth decision recorded** ([ADR-004](architecture/adr-004-auth-fastapi-authlib-google-oidc.md)):
    FastAPI + Authlib Google OIDC alongside argon2id password — **not** a Node/better-auth
    rewrite (better-auth is JS-only; a rewrite would discard hardened, tested code).
  - ✅ **Google Sign-In (OIDC) implemented** in the one FastAPI service:
    `core/config.py` (env-driven, feature-flagged), `users` schema columns
    `email`/`auth_provider`/`provider_sub` (idempotent migration + partial unique indexes),
    `core/oauth_users.py` (find-or-create/link, **5 unit tests**), `routers/auth_google.py`
    (login/callback, loaded **only when configured** so Authlib stays optional). `/me` now
    returns `email`/`auth_provider`. Front-end `auth-service.js` gains `googleLoginUrl()` +
    `captureTokenFromFragment()`. **45 passed, ruff clean.**
  - ✅ **Frontend cutover done (2026-06-29):**
    - FastAPI now **serves the owner app at a single origin** (`/`, `/manifest.json`,
      `/static/*`) so the app uses relative `/api/v1` with no CORS surface (main.py).
    - **Login screen** replaces the client-side PIN: phone/password (argon2id) + "Sign in
      with Google" + signup toggle; `app.js` gates on a real server session (`/auth/me`),
      and a Google-redirect token is captured on load.
    - **`app.js` repointed** — all 11 fetch sites now go through the shared service layer
      (`window.API`/`window.Auth` bridge via `static/shared/api-globals.js`); oversell maps
      to the 409 `INSUFFICIENT_STOCK` envelope. Zero direct `fetch` / legacy `/api/*` left.
    - **Self-verified (TestClient):** full ES-module graph serves same-origin; index carries
      the login markup + `v=4` assets; `/api/v1/items` is 401 unauth → 200 after signup.
      **45 passed, ruff clean.**
  - 🌐 **Needs a real browser (yours) to confirm — checklist:** login submits & shows the
    app; "Sign in with Google" redirects (once creds set); inventory/sales/dashboard render;
    add/edit/delete item; record sale + oversell alert; Logout returns to login; PWA install;
    camera capture (note: image-save lands in Phase 8). Run: `uvicorn app.main:app --port 8080`
    then open `http://localhost:8080/`.
  - ⏳ **Known interim gaps (by design):** item **images don't persist** until Phase 8
    (object storage); Google end-to-end needs the owner's OAuth client (env vars set).
  - **Owner action item:** create a Google Cloud OAuth client and set `GOOGLE_CLIENT_ID`,
    `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI`, `AUTH_SECRET` (see `.env.example`).

### ⬜ Phase 7 — Owner Offline & Sync Hardening
- **Goal:** Reliable offline inventory/sales with queued sync.
- **Tasks:** IndexedDB cache + offline action queue; replay on reconnect; idempotency keys
  (§4.1, §4.9) so replays don't double-post; conflict policy.
- **Deliverables:** offline queue module + sync engine.
- **Standards:** §4.9 idempotency, GUARDRAILS race-condition checks.
- **Exit:** Add item offline → reconnect → single server record; no duplicates.

### ✅ Phase 8 — Camera / Image Capture → Object Storage *(local-disk now, S3-swappable)*
- **Goal:** Clean image pipeline; stop base64-in-DB.
- **Tasks:** `getUserMedia` capture component; client compress; upload to object storage
  via `/api/v1/items/{id}/image`; HTTPS guard.
- **Deliverables:** capture component + upload service + backend endpoint.
- **Standards:** GUARDRAILS file-upload validation (type/size), §2.4 sizing.
- **Exit:** Photo → optimized object-storage URL on item; no base64 blobs persisted.
- **✅ Done (2026-06-29):** `app/core/image_storage.py` — a swappable `ImageStorage`
  interface with a `LocalDiskImageStorage` impl (env `IMAGE_STORAGE_BACKEND`, ready for an
  S3/GCS backend later, CLAUDE.md §4). Validates data-URL **type + size**, server-generated
  filenames (no path-traversal), best-effort Pillow downscale/recompress (optional dep).
  Wired into item create/update (existing camera forms now persist; old image cleaned up on
  replace). **11 tests** (decode/validation, disk round-trip, API persist/clear/reject).
  `.gitignore` keeps new uploads out of source. **57 passed, ruff clean.**
  - **Note:** images persist to disk under the served static dir (single host). The
    object-storage backend is the swap for multi-instance scale — interface is in place so
    that swap touches no callers. The earlier "camera captures don't save" gap is **closed**.

### ⬜ Phase 9 — Owner App Test Suite
- **Goal:** Meet Commercial coverage for the owner face.
- **Tasks:** Unit (services, config, offline queue), integration (auth+CRUD against test
  API), E2E top journeys; MSW mocks; deterministic time.
- **Deliverables:** `*.test.js`, `/tests/e2e/*.spec`, MSW handlers.
- **Standards:** Pillar 7 (80% services, top-5 E2E), §7.7/§7.8.
- **Exit:** Coverage thresholds met; E2E green in CI.

### ⬜ Phase 10 — TWA Wrapper for the Owner App
- **Goal:** One APK shell loading the hosted owner PWA.
- **Tasks:** Bubblewrap init from the dynamic manifest; Digital Asset Links
  (`assetlinks.json`); theming; package id `com.<brand>.owner`; local debug build.
- **Deliverables:** `android/` TWA project, asset-links deployed.
- **Standards:** ADR (§5.4), supply-chain (GUARDRAILS §6.2).
- **Exit:** Debug APK runs the owner app full-screen; asset links verified (no URL bar).

### 🟦 Phase 11 — Customer Catalog PWA Refactor *(data cutover done w/ fallback; auth/cart Bearer cutover pending)*
- **Goal:** White-label catalog on `/api/v1/catalog`, WhatsApp checkout.
- **Tasks:** Catalog/product/cart pages via services; WhatsApp deep-link prefilled from
  `settings.whatsapp_number` + cart; out-of-stock/discount badges; guest cart + optional
  auth.
- **Deliverables:** `customer-catalog/` complete.
- **Standards:** §3.x, §4.2; safe-fields guarantee (no cost/location).
- **Exit:** Browse → cart → WhatsApp works; no owner-only field ever rendered.
- **🟦 Done so far (2026-06-29) — non-breaking, because the catalog is LIVE for a real shop:**
  - Catalog **products + settings now read from `/api/v1/catalog/*`**, with a **fallback to
    the published static `data/*.json`** so it keeps working on static-only hosting / if the
    API is unreachable. `imgSrc()` handles both API (absolute) and legacy (filename) image
    URLs. API base via `<meta name="api-base">` or same-origin `/api/v1`.
  - Catalog can be **served same-origin** from FastAPI at `/catalog` (no CORS needed), and
    **configurable CORS** added for cross-origin hosting (`CORS_ALLOW_ORIGINS`).
  - Self-verified (TestClient): `/catalog/` serves, `/catalog/manifest.json` 200,
    `/api/v1/catalog/products` 200. **65 passed, ruff clean.**
  - ⬜ **Pending:** move catalog **auth + server cart** off the legacy cookie endpoints
    (`/api/auth/guest|login`, `/api/cart`) onto Bearer `/api/v1/auth` + `/api/v1/cart`
    (today they degrade gracefully; local cart + WhatsApp checkout work). Guest cart stays
    local. Browser-verify on a deployed API.
  - **Gate:** the live catalog runs on the static-JSON model for a real shop — the hard
    cutover waits for the API to be deployed + reachable; the fallback keeps it safe now.

### ⬜ Phase 12 — Catalog Distribution (WhatsApp link + installable PWA)
- **Goal:** Per-shop shareable catalog, no app store.
- **Tasks:** Per-shop slug URL; "share on WhatsApp" + "Add to Home Screen" prompts; Open
  Graph preview cards for link unfurls.
- **Deliverables:** slug routing, share flow, OG tags.
- **Standards:** white-label; §3.8 routing.
- **Exit:** A shop's link opens its branded catalog; WhatsApp unfurls a proper preview.

### ⬜ Phase 13 — Catalog Performance & Lighthouse Budget
- **Goal:** Fast, install-worthy customer experience.
- **Tasks:** `loading="lazy"` + width/height on images (CLS), route-level lazy chunks,
  image CDN sizing, perf budget in CI.
- **Deliverables:** perf budget config, optimized assets.
- **Standards:** §3.5 (image/lazy triggers), GUARDRAILS performance gate.
- **Exit:** Lighthouse mobile ≥90 perf/PWA on the catalog.

### ⏸️ Phase 14 — Multi-Tenant White-Label Provisioning (mobile)
- **Goal:** One shell serves N shops from config; zero per-shop builds.
- **Tasks:** Resolve shop from slug/subdomain → load that shop's settings → brand owner
  app & catalog at runtime; TWA shell shop-agnostic (loads shop after owner login).
- **Deliverables:** tenant-resolution layer (client) aligned with backend `shop_id`.
- **Standards:** CLAUDE.md §4 (one DB + `shop_id`), no sprawl.
- **Exit:** Two shops get distinct branding from the same deployed code/APK.
- **Gate:** Deferred until ≥1 real paying shop (Council decision, CLAUDE.md §4).

### ⏸️ Phase 15 — APK Build & Release CI/CD (cloud)
- **Goal:** Reproducible signed APK/AAB from CI, never the x86 host.
- **Tasks:** GitHub Actions Bubblewrap build → sign with keystore from secrets → upload
  AAB; keystore custody + rotation doc; version-code automation.
- **Deliverables:** `.github/workflows/android-release.yml`, signing runbook.
- **Standards:** pipeline_ops Module 3 (build-once/deploy-many), GUARDRAILS secrets.
- **Exit:** Tag → CI emits a signed AAB; keystore documented and access-controlled.

### ⏸️ Phase 16 — Play Store Listing & Compliance
- **Goal:** Publishable, compliant owner app.
- **Tasks:** Play account ($25), store assets (Hindi), data-safety form, privacy policy +
  ToS pages (DPDP), content rating; internal-testing track first.
- **Deliverables:** store listing, legal pages, internal track release.
- **Standards:** DPDP/privacy (GUARDRAILS data flow), §5 docs.
- **Exit:** App approved on internal testing track; legal pages live and linked.

### ✅ Phase 17 — Security Hardening (mobile-facing)
- **Goal:** Close client/edge security gaps for paid, multi-tenant use.
- **Tasks:** CSRF tokens for cookie flows (§4.1.1), CSP + security headers, session
  expiry/refresh, secret hygiene, rate-limit auth (5/15min), DPDP data-handling review,
  dependency/secret scan in CI.
- **Deliverables:** CSRF middleware + client wiring, header config, STRIDE threat model.
- **Standards:** GUARDRAILS Module 1–2, §4.1 rate limits.
- **Exit:** Security review checklist passes; SAST/secret scan clean.
- **✅ Done (2026-06-29):**
  - **Auth rate-limiting** (`core/rate_limit.py`): per-IP sliding window (5/15min, env-tunable)
    on login + signup → 429 `RATE_LIMITED`. In-memory now; Redis at scale (call sites stable).
  - **CSP + tightened headers** on every response (default-src 'self', frame-ancestors 'none',
    base-uri/form-action 'self', img data:/blob:). `'unsafe-inline'` retained while front-ends
    use inline handlers (removal tracked).
  - **CSRF posture documented, not cargo-culted:** the API is **Bearer-only (no ambient
    cookies)**, so classic CSRF doesn't apply; `require_auth` accepts only the header.
  - **CI dependency scan** (`pip-audit`, advisory) added; gitleaks already present.
  - **Threat model + DPDP data-handling doc** (`docs/security/threat-model.md`): PII inventory,
    STRIDE controls, retention, known gaps (DSR export/delete, HTTPS-at-edge, inline-JS removal).
  - Tests: `test_security_hardening.py` (rate-limit 429, headers/CSP). **68 passed, ruff clean.**

### ✅ Phase 18 — Observability, Push & SLOs *(backend done; FCM push deferred)*
- **Goal:** Know when mobile breaks; enable engagement.
- **Tasks:** Structured client error reporting (correlation IDs); backend structured
  logging (§4.7); FCM push for owner app (low-stock/new-order); define SLOs/error budgets.
- **Deliverables:** error tracking, FCM integration, SLO doc.
- **Standards:** §4.7, pipeline_ops Module 4 (monitoring, SLOs).
- **Exit:** Crashes/API errors surface; a test push delivers; SLOs defined.
- **✅ Done (2026-06-29):**
  - **Structured JSON logging** (`core/observability.py`): one line per request with
    `request_id`, method, path, status, `duration_ms`; `startup`/`unhandled_error` events.
  - **Request correlation IDs:** propagated from/echoed as `X-Request-ID` (unsafe values
    rejected), in 500 bodies, and surfaced on the client `ApiError.requestId`.
  - **SLO doc** (`docs/observability/slos.md`): availability/latency/abuse targets + error
    budget, derivable directly from the log fields.
  - Tests: `test_observability.py` (header present/propagated/sanitized, JSON formatter).
    **72 passed, ruff clean.**
  - ⏳ **Deferred:** FCM push (needs the TWA + FCM creds) and a hosted log/error platform
    (Sentry/Grafana) — deployment-gated.

### ⏸️ Phase 19 — Staged Rollout, Canary & Rollback
- **Goal:** Ship safely with reversibility.
- **Tasks:** Feature flags for risky features (§4.8); Play staged rollout (5%→100%); PWA
  versioned cache rollback; documented rollback protocol for app + API.
- **Deliverables:** flag config, rollout + rollback runbooks.
- **Standards:** pipeline_ops Module 3.5 (rollback), §4.8 flags.
- **Exit:** A bad release can be rolled back within documented RTO without data loss.

### ⬜ Phase 20 — Launch, Docs & Feedback Loop
- **Goal:** GA with maintainable docs and a learning loop.
- **Tasks:** `CHANGELOG.md`, directory READMEs (§5.3), user docs/video (Hindi), owner
  onboarding guide; close ADR loop; post-launch RCA → Guardrail updates.
- **Deliverables:** docs set, runbooks, post-launch review cadence.
- **Standards:** §5.3/§5.7, pipeline_ops Module 5 (knowledge loop).
- **Exit:** Public launch; docs complete; week-1 review scheduled.

---

## Sequencing & risk notes

- **Critical path:** 1→2→3→4→5 gate the rest. After 5, owner (6–10) and customer (11–13)
  tracks parallelize. 17/18/20 are cross-cutting.
- **Deferred (⏸️):** 14, 15, 16, 19 — the SaaS/multi-tenant + store-publish leap. Per the
  LLM Council and CLAUDE.md §4, **do not pull these forward before one real shop pays.**
- **Top risks:** (1) keystore loss in Phase 15 is unrecoverable — solve custody before
  first signed build; (2) DPDP/privacy (16/17) is a legal gate the moment customer phone
  numbers persist; (3) Phases 14/16 assume paying shops.

## Changelog (this document)

- 2026-06-29 — Initial 20-phase plan written.
- 2026-06-29 — Phase 1 complete (ADR-001/002/003). Phase 2 started: shared front-end
  service layer (`static/shared/api/*`) built; owner/catalog repoint pending.
- 2026-06-29 — Phase 2 finding: owner/catalog repoints are blocked on auth model (Phase 6),
  image storage (Phase 8), CORS/guest-cart (Phase 11), plus a v1 dashboard-parity gap.
  Re-sequenced owner cutover to ride Phase 6; service layer left ready.
- 2026-06-29 — Closed the v1 dashboard-parity gap (Phase 6 prep): added `total_stock_mrp`
  + low-stock `recent_items` to `/api/v1/dashboard` with tests. 40 passed, ruff clean.
  Remaining owner-cutover blockers: auth/login wiring (Phase 6), image storage (Phase 8).
- 2026-06-29 — Phase 6 backend: ADR-004 (FastAPI+Authlib, not Node rewrite). Implemented
  Google Sign-In (OIDC) feature-flagged + argon2id password; `users` migration for
  federated identity; provisioning logic with 5 tests. Front-end auth-service Google
  helpers added. 45 passed, ruff clean. Next: owner login UI + app.js repoint.
- 2026-06-29 — Phase 6 frontend cutover DONE: FastAPI serves the owner app same-origin;
  client PIN replaced by server login (password + Google) with session gating; app.js's
  11 fetch sites repointed to the shared service layer (window.API bridge). Self-verified
  via TestClient (module graph serves, auth gate enforced). 45 passed, ruff clean. Browser
  verification + Google OAuth creds pending on owner.
- 2026-06-29 — Committed the increment as 4 atomic commits (docs / dashboard / service
  layer / auth+cutover).
- 2026-06-29 — Phase 3 DONE: config-driven white-label branding (runtime-config.js applies
  shop settings to title/theme; window.Branding bridge; API de-branded; CI white-label-gate
  blocks hardcoded brand). 46 passed, ruff clean, gate clean.
- 2026-06-29 — Phase 8 DONE: swappable image storage (local disk now, S3-ready) with
  type/size validation + safe filenames; wired into item create/update; camera persistence
  gap closed; new uploads gitignored. 57 passed, ruff clean.
- 2026-06-29 — Phase 4 DONE: real PNG icons (owner + catalog), dynamic settings-branded
  owner manifest, de-branded catalog manifests/titles, duplicate static owner manifest
  removed. Also fixed the white-label CI gate (was effectively red in CI; now excludes
  runtime data/ and passes from repo root). 61 passed, ruff clean.
- 2026-06-29 — Phase 5 DONE: unified service-worker strategy (versioned v5; /api never
  cached; network-first navigations; SWR static). Fixed owner SW scope bug (now served at
  root /sw.js, scope "/"). 63 passed, ruff clean.
- 2026-06-29 — Phase 11 (partial): catalog products/settings cut over to /api/v1/catalog
  with static-JSON fallback (non-breaking for the live shop); same-origin /catalog serving
  + configurable CORS added. Auth/cart Bearer cutover deferred. 65 passed, ruff clean.
- 2026-06-29 — Phase 17 DONE: auth rate-limiting (5/15min → 429), CSP + tightened security
  headers, CI pip-audit (advisory), threat-model/DPDP doc; CSRF posture documented (Bearer-
  only, no ambient cookies). 68 passed, ruff clean.
- 2026-06-29 — Phase 18 DONE (backend): structured JSON logging + request correlation IDs
  (X-Request-ID propagated/returned, on ApiError, in 500 bodies); SLO doc. FCM push +
  hosted log platform deferred (deployment-gated). 72 passed, ruff clean.
