# Functionality, Web→Android Parity & Path to Fully-Functional

> **Version:** 2026-07-02
> **Scope:** The native customer app at `mobile/customer-app/`.
> Read with [`../../MOBILE-ARCHITECTURE.md`](../../MOBILE-ARCHITECTURE.md) (design) and
> the repo `AGENTS.md` / `CLAUDE.md` (binding guardrails).

This document answers two questions honestly:

1. **Did the app migrate everything the web app does?** — Section 1 & 2 (with a precise
   matrix, verified against the actual web source, not assumptions).
2. **What is still needed for the whole system to work fully functionally?** — Section 3
   onward (a prioritized, checkbox to-do covering the app, backend, and release).

---

## 0. What "the web app" actually is (verified from source)

There isn't one web app — there are **three** customer-relevant surfaces, and they do
**not** have identical features. This matters for judging "parity":

| Surface | File | Cart? | Auth? | WhatsApp | Notes |
|---|---|---|---|---|---|
| **GitHub Pages PWA** (production, live) | `git-pages/app.js` | ❌ No | ❌ No | Per-product enquiry from detail modal | Reads `data/view-config.json`; derives categories from `product.type` |
| **customer-view/site** (local Node :3000) | `customer-view/site/index.html` | ✅ Yes (cart page, add-to-cart) | ✅ Guest + phone/password (via :3001 API) | Cart-based order message | The richer frontend |
| **View Manager** (:3001) | `customer-view/manager/server.js` | — | — | — | **Admin/owner tool**, not a customer surface |

**The Android app targets the customer catalog experience and implements the _superset_
of the two customer frontends' browse features + the `customer-view/site` cart.** It does
**not** implement the admin/View-Manager tooling (that is the future *admin app*, blocked
on backend auth — see §4).

Verified facts that shaped the app:
- Production `git-pages/data/products.json` contains **only** these keys:
  `id, name, type, description, price, mrp, discount_percent, image_url, featured, badge,
  sort_order`. It carries **no** `location`, `quantity`, or `purchase_cost` — so the app's
  model is already aligned with the "never expose sensitive fields" guardrail. ✅
- `stock_status: "out_of_stock"` appears only when a product is out of stock (absent = in
  stock). The app parses this defensively. ✅

---

## 1. Feature parity matrix — Customer web → Android

Legend: ✅ done · 🟡 partial/enhanced · ⬜ not yet · ⛔ intentionally out of scope

| # | Customer web feature | Android status | Notes |
|---|---|---|---|
| 1 | Load catalog from generated JSON | ✅ | `CatalogRepository`, cache-then-network |
| 2 | Product grid (image, name, price) | ✅ | `ProductAdapter`, 2-col `RecyclerView` |
| 3 | Struck-through MRP + discount badge | ✅ | Honors `show_mrp`, `show_discount_badges` |
| 4 | Custom per-product badge | ✅ | `badge` field |
| 5 | Featured ordering | ✅ | `CatalogFilter`: featured → sort_order → name |
| 6 | Out-of-stock badge + dim + hide buy | ✅ | Detail hides add-to-cart when OOS |
| 7 | Text search (name/description) | ✅ | Honors `show_search` |
| 8 | Category filter | 🟡 | Uses `categories.json`; **web PWA derives categories from `product.type`** — see Gap G1 |
| 9 | Product detail view | ✅ | `ProductDetailActivity` |
| 10 | Currency symbol from settings | ✅ | `settings.currency_symbol` |
| 11 | White-label title/subtitle | ✅ | `settings.app_title/app_subtitle` |
| 12 | Settings-driven display toggles | ✅ | `show_images/mrp/description/...` |
| 13 | Cart (add, qty, remove, total) | ✅ | Matches `customer-view/site`; **git-pages PWA has no cart** — this is an enhancement over production |
| 14 | Cart persistence across restarts | ✅ | `CartStore` (SharedPreferences) |
| 15 | WhatsApp checkout | ✅ | Cart-based itemized message → `wa.me` |
| 16 | Offline browsing | 🟡→✅ | Disk cache serves catalog + images offline; **better than the PWA's network-first SW** |
| 17 | Version-based cache-busting | ✅ | `version.json → v`, `CatalogSync` |
| 18 | Loading / empty / error states | ✅ | Skeleton, empty-search, offline banner, retry |
| 19 | Image lazy-load + placeholder | ✅ | Custom `ImageLoader` (LRU + disk) |
| 20 | Per-product `location` tag (📍) | ⬜ | git-pages `app.js` renders it **if present**, but production data has none and `CLAUDE.md` marks `location` sensitive — see Decision D1 |
| 21 | `view-config.json` hidden-products filter | ⬜ | git-pages reads it; app reads pipeline-filtered `products.json` — see Gap G2 |
| 22 | Guest / phone+password auth | ⛔ | Customer app is anonymous by design (§4); auth server is unauthenticated-localhost today |
| 23 | Install as app / home-screen icon | ✅✅ | Native APK — strictly better than PWA "Add to Home Screen" |

**Bottom line:** every **customer-facing browse/search/detail/cart/checkout/offline**
feature is migrated, and several are improved (native install, true offline, image cache).
Three items are open by *decision* or *minor gap* (D1, G1, G2 below), and admin/auth
features are deliberately out of scope for a customer app.

---

## 2. What was NOT migrated (and why) — full honesty

These live in the **admin/manager/backend**, not the customer catalog. They are the *shop
owner's* tools. None belong in a customer app; they define the future **admin app** (§4).

| Web capability | Where it lives | Why not in this app |
|---|---|---|
| Inventory CRUD (add/edit/delete, photo upload) | Flask `app.py` :8080 | Owner-only; needs authenticated backend |
| Sales recording + stock auto-deduction | Flask `app.py` | Owner-only |
| Dashboard (revenue, stock value, breakdown) | Flask `app.py` | Owner-only |
| Product curation (visible/featured/badge) | View Manager :3001 | Owner-only |
| Sync + Publish to GitHub Pages | View Manager :3001 | Owner-only; runs `git push` |
| Google Drive backup | `auto_backup.py` | Server-side infra |
| Customer accounts (guest/registered) | `:3001 /api/auth/*` | Server is unauthenticated-localhost; unsafe to expose (see `CLAUDE.md` §3) |

### Decisions & gaps to resolve (owner input needed)

- **D1 — per-product `location`:** The live `git-pages` PWA *would* render a 📍 location
  tag, but (a) production data has no `location` and (b) `CLAUDE.md` lists `location` as a
  field that must stay out of customer-facing output. **Recommendation: keep it out.**
  If the owner means "shop address" (not per-item shelf location), surface
  `settings.shop_location` on an About/footer screen instead (see T-App-4).
- **G1 — category source:** App reads `categories.json`; the PWA derives categories from
  `product.type`. If `categories.json` is ever stale, the chip bar could differ. **Fix:**
  fall back to distinct `product.type` values when `categories.json` is missing/empty
  (small, safe — T-App-1).
- **G2 — `view-config.json` vs `settings.json`:** Two config files coexist (a known issue
  in `AGENTS.md` "Partially Implemented"). `products.json` is already visibility-filtered
  by `generate.py`, so the app is correct today, but the **backend should converge on one
  config file** to avoid drift (T-Back-5).

---

## 3. Path to "fully functional" — prioritized to-do

Grouped by owner. ✅ = already done in this pass.

### A. Android app polish (T-App-*)

- [ ] **T-App-1 — Category fallback (G1):** derive chips from `product.type` when
  `categories.json` is empty/missing. *(Small; robustness.)*
- [ ] **T-App-2 — Instrumented UI tests:** Espresso smoke test (launch → grid → detail →
  add to cart → cart → WhatsApp intent fired). Complements the 22 JVM unit tests.
- [ ] **T-App-3 — Crash reporting:** add a lightweight, privacy-respecting crash log
  (local file or self-hosted) — no third-party SDK unless approved (GUARDRAILS 6.2).
- [ ] **T-App-4 — About / shop info screen:** show `settings.shop_location`,
  `footer_text`, WhatsApp — resolves D1 cleanly.
- [ ] **T-App-5 — App-update nudge:** compare `versionCode` to a small JSON on Pages so
  users on old APKs get a "please update" prompt (until Play Store auto-update exists).
- [ ] **T-App-6 — Accessibility pass:** TalkBack labels on icon buttons, contrast check,
  large-font layout test (WCAG per `ACCESSIBILITY_STANDARDS.md`).
- [x] Signed release APK builds reproducibly (`assembleProdRelease`).
- [x] Branded launcher + adaptive icon; 512 Play-Store icon.
- [x] 22 unit tests green against real production fixtures.

### B. Backend / data pipeline (T-Back-*) — unblocks multi-tenant & admin app

- [ ] **T-Back-1 — Server-side auth on Flask & View Manager** *(highest priority,
  security).* No `/api/*` route may be open. Prereq for the admin app and any hosting.
- [ ] **T-Back-2 — Replace hardcoded `/root/...` paths** with `__file__`-relative; move
  `AUTH_SECRET` to env. The app is fine, but the pipeline that feeds it isn't portable.
- [ ] **T-Back-3 — Host the catalog under a stable, versioned URL** (custom domain or the
  existing Pages URL) and, for multi-tenant, one path per shop
  (`/<shop>/data/*.json`). The app's `CATALOG_BASE_URL` flavor field is ready for this.
- [ ] **T-Back-4 — Add a real order channel (optional, beyond WhatsApp):** if orders
  should be tracked, add an authenticated `POST /api/v1/orders`. Today, ordering is
  WhatsApp-only by design (parity with web).
- [ ] **T-Back-5 — Converge `view-config.json` and `settings.json`** into one source (G2).
- [ ] **T-Back-6 — Consolidate the two DBs / retire the copy pipeline** (the V01 FastAPI
  direction) so a new field doesn't need editing across five files.

### C. Release & store (T-Rel-*)

- [ ] **T-Rel-1 — Move signing creds off the machine** into CI secrets; never commit the
  keystore (already git-ignored). Current dev keystore lives at `~/.arv-keystore/`.
- [ ] **T-Rel-2 — Enable CI** (`.github/workflows/android.yml` exists, manual-trigger):
  switch to PR/push on `mobile/**` after human approval.
- [ ] **T-Rel-3 — Build an AAB** (`bundleProdRelease`) for Play Store (smaller installs).
- [ ] **T-Rel-4 — Play listing:** screenshots (≥5, dark theme), short/full description,
  content rating (Everyone), **Data-safety: no data collected** (cart is local; no PII).
- [ ] **T-Rel-5 — Privacy policy URL** (host on the Pages site).
- [ ] **T-Rel-6 — Device QA:** Android 7 (API 24) low-RAM + a current device; WhatsApp
  installed *and* not-installed paths.

### D. Future — Admin app (Option B/C from MOBILE-ARCHITECTURE §0.5)

Blocked entirely on **T-Back-1**. Once the backend is authenticated and hosted:
- [ ] Owner login (server-validated, argon2/bcrypt — not the current client-side PIN).
- [ ] Inventory CRUD + camera upload against `/api/v1`.
- [ ] Sales entry, dashboard.
- [ ] Curate visibility/featured/badge; trigger publish.
- [ ] Reuse this repo's `data/`, `core/`, `util/` layers (why native > TWA).

---

## 4. Why the customer app is anonymous (not a gap)

The live `git-pages` PWA has **no login** — customers just browse and tap WhatsApp. The
richer `customer-view/site` offers optional guest/registered accounts, but that auth
server (`:3001`) is unauthenticated and localhost-only today (`CLAUDE.md` §3). Shipping a
mobile client that logs into an insecure endpoint would violate the repo's own guardrails.
So the app is intentionally anonymous, storing **no PII** and keeping the cart local — this
also makes the Play Store "Data safety" declaration trivially "no data collected." When the
backend gains real auth (T-Back-1), optional accounts can be added.

---

## 5. Current build status (this pass)

```
✅ Android SDK installed (platform-34, build-tools 34.0.0)
✅ ./gradlew test            → 22 tests, 0 failures (real production fixtures)
✅ ./gradlew assembleDevDebug / assembleProdDebug → APKs (5.9 MB)
✅ ./gradlew assembleProdRelease → SIGNED, minified APK (1.6 MB)
✅ apksigner verify          → Signer #1 present, SHA-256 recorded
✅ Branded tote-bag launcher + adaptive icon + 512 store icon
```

**Definition of "fully functional MVP shippable":** T-App-1, T-App-2, T-App-6, T-Rel-3,
T-Rel-4, T-Rel-5, T-Rel-6. **Definition of "platform fully functional" (multi-shop +
admin):** the T-Back-* block, then Section D.
