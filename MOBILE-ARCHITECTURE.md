# MOBILE-ARCHITECTURE.md — ARV Shop Manager Android App

> **Document version:** 2026-07-02
> **Status:** Plan approved-for-build → implementation in `mobile/customer-app/`
> **Governing docs:** `CLAUDE.md`, `AGENTS.md` (binding), `~/Documents/Prompts/` standards
> **Risk tier:** Commercial/Production

---

## Phase 0 — Context Reconstruction (verified against source, 2026-07-02)

### Admin flow
The shop owner runs a Flask app (`shop-manager/backend/app.py`, port 8080): PIN lock
(client-side only), inventory CRUD with camera/photo upload (Pillow resize ≤400×400),
sales recording with stock auto-deduction, dashboard stats. Every DB write fires a
background Google Drive backup (`auto_backup.py`, external `hermes` dependency).

### Customer flow
Customers browse a static PWA — locally via `customer-view/site` (port 3000) or in
production from GitHub Pages (`https://singhvishalvikram.github.io/Cinema123BW`). The
PWA fetches `data/products.json`, `categories.json`, `settings.json`; a service worker
caches network-first; `data/version.json` (`{"v": <unix-ts>, "at": ..., "count": ...}`)
is the cache-bust signal. Cart is local; checkout is a WhatsApp deep link pre-filled
with the order enquiry (number from `settings.whatsapp_number`).

### View Manager
Node server (`customer-view/manager/server.js`, port 3001, zero npm deps) curates
visibility/featured/badge per product and triggers the pipeline + publish.

### Data pipeline (the chain — never bypass, never hand-edit outputs)

```
shop.db → import.py → customer-view.db → generate.py → site/data/*.json
        → publish (git push gh-pages) → git-pages/data/*.json → PWA + service worker
```

### Verified production data shapes (from `git-pages/data/`, 2026-07-02)

```jsonc
// products.json — ARRAY of:
{ "id": 54, "name": "AKARI Led Torch", "type": "Electronics",
  "description": "...", "price": 325.0, "mrp": 390.0, "discount_percent": 17,
  "image_url": "/images/item_54_....jpg",       // RELATIVE to catalog base URL
  "featured": 0, "badge": "…", "sort_order": 0 }
  // "stock_status" MAY appear ("out_of_stock"); absent ⇒ in stock. Parse defensively.

// categories.json — ARRAY of: { "name", "display_name", "sort_order" }
// settings.json  — FLAT map of STRINGS (booleans are "0"/"1"): app_title,
//   app_subtitle, whatsapp_number, shop_location, theme_color, currency_symbol("₹"),
//   show_search, show_category_filter, show_discount_badges, show_mrp,
//   show_description, show_images, products_per_row, max_products, footer_text …
// version.json   — { "v": 1782471119, "at": "...", "count": 51 }
```

### Roadmap alignment
`AGENTS.md` Phase 4 targets an Android app. It recommends TWA/Bubblewrap; this plan
supersedes that with a **native Java app** (rationale in Phase 0.5 below) while keeping
the same integration surface: **read-only static JSON on GitHub Pages + WhatsApp
checkout. Zero backend changes. Zero pipeline changes.**

### Constraints inherited (binding)
- Never commit DBs/backups/images/.env; never hand-edit generated JSON.
- No changes to Flask/Node servers; no new unauthenticated backend routes.
- No hardcoded secrets or absolute paths; config via `BuildConfig`/gradle properties.
- Commercial tier: input validation, HTTPS-only, structured errors, tests required.

---

## Phase 0.5 — Architecture Decision: One App vs Two

| Option | Scope | Complexity/Risk | Roadmap fit | Verdict |
|---|---|---|---|---|
| **A. Customer catalog app (native Java)** | Browse, search, cart, WhatsApp checkout from static JSON | **Low** — read-only HTTPS, no auth, no PII stored, no backend edits | Direct: AGENTS.md Phase 4, same surface as TWA plan | ✅ **MVP — build now** |
| B. Admin app (inventory on phone) | Talks to Flask `/api/*` | **High** — Flask has NO server-side auth; exposing it to a phone network is a live vulnerability. Blocked on backend Phase 1 (server-side PIN/session) + hosted/reachable backend | Roadmap Phase 2+ prerequisite | ⏸ Future — **blocked on backend auth** |
| C. Two apps | A + B | Sum of both | End-state (client transcript: shopkeepers need admin app too) | 🎯 End-state; reach via A first |

**Decision: Option A now, evolving to Option C.** The customer app is shippable with
zero backend risk and validates the whole mobile pipeline (build, signing, Play Store).
The admin app is the *real* client goal but is **hard-blocked** by the "never expose
unauthenticated `/api/*`" guardrail — it becomes Phases 21+ after backend
stabilization (server-side auth, env config, hosted API under `/api/v1`).

**Why native Java instead of TWA:** (1) the requirement is a real app with smooth
native lists, offline cart, and room to grow into the admin app — TWA is a browser
shell with no code reuse toward Option B; (2) TWA is currently **blocked anyway**
(`git-pages/manifest.json` has `"icons": []` and no `assetlinks.json`); (3) native
keeps zero dependence on the PWA's service-worker quirks. TWA remains a cheap fallback.

---

## Android Technical Standards (this project)

| Concern | Standard |
|---|---|
| Language / build | **Java 11 syntax, Gradle + AGP 8.5, single `app` module** (multi-module is unjustified at this size — revisit when admin flavor lands) |
| minSdk / target | **minSdk 24** (covers ~97% of Indian Android devices, incl. cheap handsets), targetSdk 34, compileSdk 34 |
| Architecture | **MVVM**: Activity → ViewModel (AndroidX Lifecycle) → Repository → {remote HTTPS, disk cache, SharedPreferences}. Manual constructor injection via `ServiceLocator` — no Dagger/Hilt (unjustified bloat at this scope) |
| UI | XML layouts, `RecyclerView` grid (2 cols), Material Components, dark theme matching PWA (`#0f0f1a` bg, `#6366f1` primary) |
| Networking | `HttpsURLConnection` + `org.json` (both in the platform). **No Retrofit/OkHttp/Gson/Glide** — mirrors the repo's zero-dependency ethos; every avoided dep is one less supply-chain risk (GUARDRAILS 6.2). Custom `ImageLoader` (memory LRU + disk cache + executor pool) |
| Threading | `AppExecutors` (fixed pools); **never** network/disk on main thread; results via `LiveData.postValue` |
| Offline | Cache-then-network: render cached catalog instantly, then check `version.json`; refetch only when `v` changes. Images disk-cached. Cart is 100% local |
| Config | `BuildConfig.CATALOG_BASE_URL` from product flavors (`dev`/`prod`); overridable via `-PcatalogBaseUrl=`; no secrets exist in this app |
| Tests | JVM unit tests (JUnit 4) for parser, cart math, WhatsApp message builder, version logic — using **real fixture JSON** copied from production data |

### Dependency justification (complete list)

| Dependency | Why |
|---|---|
| `androidx.appcompat` | Activity/theme baseline |
| `com.google.android.material` | Cards, chips, toolbar, snackbar — visual parity with PWA badges/cards |
| `androidx.recyclerview` | Catalog grid + cart list |
| `androidx.constraintlayout` | Flat, performant item layouts |
| `androidx.lifecycle:*-viewmodel/livedata` | MVVM state holder surviving rotation |
| `androidx.swiperefreshlayout` | Pull-to-refresh → version check |
| `junit:junit` (test) | Unit tests |

---

## The 20-Phase Plan

Phases 1–13 and 16–17 are **implemented in this pass** (source complete in
`mobile/customer-app/`); build-machine verification steps are listed per phase and
consolidated in "Build & Verify" below. Touches column: which existing system parts
each phase touches — **all phases touch none of Flask/Node/pipeline** unless noted.

| # | Phase | Objective | Entry → Exit criteria | Verification |
|---|---|---|---|---|
| 01 | **Project scaffolding** | Gradle root+app module, manifest, `.gitignore` (no keystores/local.properties committed) | repo clean → `./gradlew tasks` runs | Gradle sync OK on build machine |
| 02 | **Core architecture** | `ServiceLocator`, `AppExecutors`, `Result<T>`, package layout (`data/`, `ui/`, `util/`) | 01 done → skeleton compiles | `assembleDebug` compiles; no logic yet |
| 03 | **Data layer** | Models matching production JSON exactly; `CatalogParser`; `HttpClient` (HTTPS, timeouts); `DiskCache` | 02 → parser round-trips real fixtures | **Unit tests:** `CatalogParserTest` against fixture copied from `git-pages/data/` |
| 04 | **Catalog screen (dummy data)** | `MainActivity` + `ProductAdapter` grid renders hardcoded list; loading skeleton | 03 → grid scrolls @60fps | Manual: launch, scroll; Layout Inspector |
| 05 | **Real data integration** | `CatalogRepository` cache-then-network from `BuildConfig.CATALOG_BASE_URL/data/*.json`; relative `image_url` resolution | 04 → live products render | Manual: airplane-mode cold start (cached), fresh install (network); `curl` parity check |
| 06 | **Product detail screen** | Tap card → detail: image, price/MRP/discount, description, badge, add-to-cart | 05 → navigation works | Manual walk; rotation state preserved via ViewModel |
| 07 | **Cart model + persistence** | `CartStore` (SharedPreferences JSON), `CartRepository` LiveData, qty stepper, cart screen | 06 → cart survives process death | **Unit tests:** `CartCalculatorTest`; manual kill-and-relaunch |
| 08 | **WhatsApp checkout** | Build order message (items, qty, line totals, grand total, ₹) → `wa.me/<settings.whatsapp_number>` intent; graceful fallback if WhatsApp absent | 07 → intent opens WhatsApp with correct text | **Unit tests:** `WhatsAppCheckoutTest` (message + URL encoding); manual on device |
| 09 | **Sync via version.json** | On resume + pull-to-refresh: fetch `version.json`; refetch catalog only if `v` changed; show "Updated" snackbar | 05 → no redundant full fetches | Unit test on version-compare logic; manual: publish from View Manager → app refresh (touches: reads pipeline output only) |
| 10 | **Offline caching** | Full catalog + images served from disk cache when offline; offline banner | 09 → usable with no network | Manual: airplane mode browse + cart + view images |
| 11 | **Branding/theming** | Dark theme `#0f0f1a`/`#6366f1`, discount + custom badges, `settings.json`-driven title/currency/toggles (`show_mrp`, `show_discount_badges`…) | 05 → visual parity with PWA | Side-by-side vs `singhvishalvikram.github.io/Cinema123BW` |
| 12 | **Error handling & empty states** | Typed errors (network/parse/empty), retry affordance, empty-search state, out-of-stock dimming + hidden add-to-cart | 11 → no crash on bad JSON/timeouts | Unit: parser rejects malformed rows individually; manual: block network mid-load |
| 13 | **Performance** | ViewHolder hygiene, image request cancellation on rebind, `DiffUtil`, downsampled bitmaps | 12 → smooth on low-RAM device | Profile on API 24 emulator, 2GB RAM profile |
| 14 | **Auth (customer app: N/A)** | Explicitly **skipped for MVP** — customer app is anonymous by design; guest/registered auth (`:3001 /api/auth/*`) deferred: that server is unauthenticated-localhost today (guardrail) | — | Documented decision; revisit with admin app |
| 15 | **Admin features** | **Deferred** — blocked on backend server-side auth (see Phase 0.5, Option B) | backend Phase 1 done → new plan doc | > TODO: requires hosted, authenticated API |
| 16 | **Testing** | JVM suite: parser, cart math, checkout builder, version logic; fixtures = real production JSON | 03–09 → `./gradlew test` green | `./gradlew test` on build machine |
| 17 | **Build configs** | Flavors `dev`/`prod` (base URL), buildTypes debug/release (minify+shrink on release), proguard rules | 01 → 4 variants assemble | `./gradlew assembleProdRelease` (unsigned) |
| 18 | **Signing & keystore** | Keystore generated **by the human, never committed**; `signingConfigs` read from `~/.gradle/gradle.properties` or env | 17 → signed release APK | > TODO (human): `keytool -genkeypair …`; SHA-256 fingerprint recorded for future `assetlinks.json` |
| 19 | **CI/CD placeholder** | `.github/workflows/android.yml` — build+test on PR touching `mobile/**`; APK artifact upload. **Requires human approval before enabling** (repo has no CI today) | 16,17 → workflow file exists, disabled-by-default via `workflow_dispatch` | Dry-run on fork > TODO |
| 20 | **Final QA + Play Store readiness** | QA checklist (below), store listing assets, privacy policy (app stores no PII — cart is local), versionCode discipline | all → checklist signed off | Human QA pass on ≥2 physical devices |

### Key UX flows (Phase 04/06/07/08 design)

- **Browse:** cold start → cached grid instantly (or skeleton) → 2-col cards: image,
  name, ₹price, struck-through MRP, `-17%` chip, custom badge, featured tint,
  out-of-stock dim. Search bar + category chips (honoring `show_search`/`show_category_filter`).
- **Detail:** hero image → name/badges → price row → description → sticky "Add to cart".
- **Cart:** line items with qty steppers, live total, "Order on WhatsApp" primary CTA.
- **Checkout:** opens WhatsApp with pre-filled enquiry; order fulfillment stays
  human-to-human (matches PWA behavior exactly — no order backend exists).

### Build & Verify (single-command, repeatable)

```bash
cd mobile/customer-app

# One-time on a machine with JDK 17 + Android SDK (or open in Android Studio):
gradle wrapper --gradle-version 8.7   # generates gradle-wrapper.jar (binary, then commit)

./gradlew test                        # JVM unit tests (Phases 03,07,08,09,16)
./gradlew assembleDevDebug            # debug APK → app/build/outputs/apk/dev/debug/
./gradlew assembleProdRelease         # release (needs signing config, Phase 18)
```

> **TODO (environment):** This workstation has no JDK/Android SDK, so compilation is
> verified on the build machine per the commands above. All other verification
> (JSON-fixture parity with production data, structure, guardrail compliance) was
> performed here.

### Play Store readiness checklist (Phase 20)

- [ ] App icon (512), feature graphic, ≥5 screenshots (dark theme)
- [ ] Content rating: Everyone; Data-safety form: **no data collected** (cart local-only)
- [ ] Privacy policy URL (can live on the GitHub Pages site)
- [ ] `versionCode`/`versionName` bumped; release signed with the Phase 18 keystore
- [ ] Tested: Android 7 (API 24) low-RAM device + current flagship
- [ ] WhatsApp installed & not-installed paths both verified
