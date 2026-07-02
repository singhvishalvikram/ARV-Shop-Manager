# ADMIN-APP-BLUEPRINT.md — ARV Shop Manager (Owner) Android App

> **Version:** 2026-07-02 · **Risk tier:** Commercial/Production
> **Governing standards:** `Prompts/GUARDRAILS.md`, `CODING_STANDARDS.md`,
> `pipeline_ops.md`, `prompt_design.md`. Produced via the Discovery → PRD → RFC →
> Roadmap → TDD pipeline (`prompt_design.md`). No shortcuts; not an MVP.

---

## Phase 0 — Discovery (verified, not assumed)

### The decisive finding
The **V01 FastAPI backend already IS the production API** and is fully tested:
`./ shop-manager/backend` — **75 pytest tests pass**, `/api/v1`, argon2id passwords,
server-side session tokens (`Authorization: Bearer`), rate-limited auth, universal
response envelope, idempotency keys, RBAC (`role='owner'`), image-storage abstraction.

**Architecture decision (ADR-M1):** The admin app is a **native Java client of V01's
`/api/v1`** — we do **not** rebuild auth in the legacy Flask app (that would discard 75
tests of secure, working code — the real shortcut). The app lives in **this repo**
(`mobile/admin-app/`) beside the API it depends on, sharing one deploy/contract.

### Verified API contract (live, `curl` against a running server, 2026-07-02)
Envelope everywhere: `{"success":bool,"data":<T>,"error":{"code","message","details"}|null}`

| Method & path | Auth | Body → Data |
|---|---|---|
| `POST /api/v1/auth/signup` | public (rate-limited) | `{phone,password,name}` → `{token,user{id,phone,name,role}}` |
| `POST /api/v1/auth/login` | public (rate-limited) | `{phone,password}` → `{token,user}` |
| `POST /api/v1/auth/logout` | Bearer | — |
| `GET  /api/v1/auth/me` | Bearer | `{user}` |
| `GET  /api/v1/items?search=` | Bearer | `[item]` |
| `GET  /api/v1/items/{id}` | Bearer | `item` |
| `POST /api/v1/items` | Bearer + `Idempotency-Key` | `ItemCreate` → `item` |
| `PUT  /api/v1/items/{id}` | Bearer | `ItemUpdate` (incl. merchandising) → `item` |
| `DELETE /api/v1/items/{id}` | Bearer | — |
| `POST /api/v1/sales` | Bearer | `{item_id,quantity,price,description}` → `{recorded}` (409 `INSUFFICIENT_STOCK`) |
| `GET  /api/v1/sales` | Bearer | `[sale]` |
| `GET  /api/v1/dashboard` | Bearer | stats (`total_items,total_quantity,total_stock_value,total_stock_cost,total_stock_mrp,today_revenue,type_breakdown[],recent_items[]`) |
| `GET/POST /api/v1/settings` | Bearer | `{key:value}` (white-label + display toggles) |

**Item shape (live):** `id,name,type,description,price,mrp,purchase_cost,image_url,
location,quantity,created_at,updated_at,visible(1/0),featured(1/0),badge,sort_order,
title_override,description_override,stock_status`.

**Publish model:** V01 serves the customer catalog **live** from the same DB
(`/api/v1/catalog/*`). There is **no git-push publish step** — "publish/hide a product"
= set `visible` via `PUT /items/{id}`. This removes the legacy import→generate→push
pipeline entirely (a major simplification the admin app inherits).

### Personas & success metric
- **Persona:** shop owner on a low-end Android phone (API 24+), often on flaky 3G,
  no computer. Must add/edit stock, record sales, see today's numbers, control what
  customers see — all from the phone.
- **Success:** owner completes "add product with photo → mark visible → record a sale"
  end-to-end on a mid-range device over 3G, with the action surviving a dropped
  connection (offline queue).

---

## Phase 1 — PRD (functional requirements, "SHALL")

1. **Auth** — SHALL log in with phone+password over HTTPS; store the session token in
   private storage; attach `Bearer` to every call; auto-logout + return to login on 401.
2. **Dashboard** — SHALL show total items, stock value/cost/MRP, today's revenue, type
   breakdown, and low-stock items.
3. **Inventory** — SHALL list/search items; add (name, type, price, qty, description,
   mrp, purchase_cost, location, photo); edit; delete (with confirm).
4. **Photos** — SHALL capture from camera or gallery, downscale client-side, send as
   base64; SHALL degrade gracefully if the device has no camera.
5. **Sales** — SHALL record a sale (item, qty, price); SHALL surface `INSUFFICIENT_STOCK`
   (409) clearly; stock decrements server-side.
6. **Curation** — SHALL toggle `visible`, `featured`, set `badge`, `sort_order`,
   title/description overrides (customer-facing merchandising).
7. **Settings** — SHALL read/write white-label + display toggles (`app_title`,
   `whatsapp_number`, `currency_symbol`, `show_*`, `products_per_row`, …).
8. **Offline resilience** — item creation SHALL queue when offline and flush with a
   stable `Idempotency-Key` so a retry never double-creates (backend supports this).
9. **Security (from GUARDRAILS)** — HTTPS only; no secrets/tokens in logs; token in
   `EncryptedSharedPreferences`; server-side validation is authoritative; the app never
   trusts client state for authorization.

### Out of scope (documented, not silently dropped)
Multi-user roles beyond owner; payments/billing; push notifications; the customer
catalog (that's the separate customer app). These are future phases.

---

## Phase 2 — RFC (technical design)

- **Language/build:** Java 11, Gradle + AGP 8.5, single `app` module, minSdk 24/target 34.
- **Pattern:** MVVM — Activity → ViewModel (Lifecycle+LiveData) → Repository → {ApiClient,
  SessionStore, OfflineQueue}. Manual DI via `ServiceLocator` (no Hilt — scope-appropriate).
- **Networking:** `HttpsURLConnection` + `org.json` (zero third-party HTTP/JSON deps —
  supply-chain minimalism, GUARDRAILS 6.2). `ApiClient` centralizes: base URL, `Bearer`
  header, envelope parse, typed `ApiException(code,message,httpStatus)`, 401 → session
  clear. `Idempotency-Key` (UUID) on item create.
- **Security:** `EncryptedSharedPreferences` (androidx.security) for the token;
  `usesCleartextTraffic=false`; base URL from build flavor (`dev`=`10.0.2.2:8000`,
  `prod`=configured HTTPS host); no token/PII in logcat (redacting logger).
- **Config source of truth:** the Pydantic schemas in `app/schemas.py` — the Java models
  mirror them exactly (field names, types, optionality).
- **Threading:** `AppExecutors` pools; results via `LiveData.postValue`; no network on main.
- **Testing:** JVM unit tests (JUnit) for envelope parsing, model mapping, image encoder,
  offline queue, validators — using fixtures captured from the **live** API. Backend
  integration proven by the existing 75 pytest tests + documented `curl` verification.

---

## Phase 3 — Roadmap (phases → subtasks, bottom-up)

Each subtask: single purpose, verifiable. ✅ built & verified this pass · ⬜ next.

### P1 Scaffolding
- ✅ 1.1 Gradle root/app, `settings.gradle`, wrapper, `.gitignore` (no keystore/local.props)
- ✅ 1.2 Manifest (INTERNET only), theme (dark, brand indigo), string/color/dimens
### P2 Core
- ✅ 2.1 `App`, `ServiceLocator`, `AppExecutors`, `Result<T>`
- ✅ 2.2 `SessionStore` (EncryptedSharedPreferences: token + user), `Session` state
### P3 Network
- ✅ 3.1 `ApiClient` (GET/POST/PUT/DELETE, Bearer, envelope, `ApiException`, 401 handling)
- ✅ 3.2 Models mirroring schemas: `Item`, `DashboardStats`, `AuthUser`, `Sale`, `ApiError`
- ✅ 3.3 `JsonMap` parse helpers; `Idempotency-Key` support
### P4 Auth
- ✅ 4.1 `AuthRepository` (login/logout/me), `LoginActivity`, `AuthViewModel`
- ✅ 4.2 Session bootstrap (skip login if token valid) + global 401 → login
### P5 Dashboard
- ✅ 5.1 `DashboardRepository`, `DashboardActivity`, `DashboardViewModel`, stat tiles
### P6 Inventory
- ✅ 6.1 `InventoryRepository`, `InventoryListActivity`, `ItemAdapter`, search
- ✅ 6.2 `ItemEditActivity` add/edit form + validation, `ItemEditViewModel`
- ✅ 6.3 Delete with confirm
### P7 Photos
- ✅ 7.1 `ImageEncoder` (downscale + base64), camera/gallery pick, `FileProvider`
### P8 Sales
- ✅ 8.1 `SalesRepository`, record-sale flow, `INSUFFICIENT_STOCK` handling
### P9 Curation
- ✅ 9.1 Visibility/featured/badge/sort/overrides on the edit screen (item PUT)
### P10 Settings
- ✅ 10.1 `SettingsRepository`, `SettingsActivity` (white-label + toggles)
### P11 Offline queue
- ✅ 11.1 `OfflineQueue` (persist pending item creates + idempotency key), flush on connectivity
### P12 Tests & build
- ✅ 12.1 JVM unit tests (envelope, models, image encoder, queue, validators)
- ✅ 12.2 `assembleDevDebug` / signed `assembleProdRelease`
### P13 Release/CI ⬜ (next)
- ⬜ 13.1 Espresso smoke test (login→add→sale) on emulator
- ⬜ 13.2 CI workflow (build+test on `mobile/**`), AAB, Play listing, privacy policy

See `MIGRATION-VERIFICATION.md` for the web-feature → admin-app parity matrix and the
end-to-end test evidence.
