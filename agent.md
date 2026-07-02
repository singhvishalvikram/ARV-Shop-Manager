# ARV Shop Manager — Agent Guide

This document instructs a developer or AI agent on how to work with the ARV Shop Manager codebase.

> **Read [`CLAUDE.md`](CLAUDE.md) first** — it is the binding ruleset (Enterprise Coding
> Standards, Commercial Tier) and the precedence authority. On any conflict, CLAUDE.md and
> the Standards win over this guide.

---

## 0. CURRENT ARCHITECTURE (authoritative) — single consolidated app

The system has been consolidated from three servers + two databases into **one FastAPI
service backed by one SQLite database**. The legacy multi-server design described in the
sections below (Flask `:8080`, Node `:3001`/`:3000`, `import.py` → `customer-view.db` →
`generate.py`) is **being retired** — treat those sections as historical reference only.

### Shape: one brain, two faces

- **One backend**: `shop-manager/backend/app/` (FastAPI), all routes under `/api/v1`.
- **One database**: `shop-manager/backend/shop.db` (SQLite WAL). Schema in `app/db.py`.
- **Two front-end faces** (kept separate by trust boundary): owner admin UI and public
  customer catalog. The catalog (`/api/v1/catalog/*`) is unauthenticated and must expose
  **only** customer-safe fields — never `purchase_cost`, `location`, or raw `quantity`.

### Package layout

```
shop-manager/backend/
├── domain.py                 # pure business rules (stock_status, discount_percent)
├── app/
│   ├── main.py               # FastAPI app, exception handlers, security headers, router wiring
│   ├── db.py                 # connection + init_schema (parameterized queries only)
│   ├── schemas.py            # Pydantic models (validation source of truth)
│   ├── core/
│   │   ├── config.py         # env-driven settings (no hardcoded paths)
│   │   ├── envelope.py       # Universal Response Envelope {success,data,error}
│   │   ├── errors.py         # central error-code registry + AppError
│   │   └── security.py       # argon2id hashing, sessions, require_auth dependency
│   └── routers/
│       ├── auth.py  items.py  sales.py  dashboard.py   # owner (authed)
│       ├── settings.py                                  # owner (authed)
│       ├── catalog.py  cart.py                          # customer (public / per-user)
│       └── health.py                                    # public
└── tests/                    # pytest (domain, security, items, sales, auth, catalog, cart)
```

### Run & test

```bash
cd shop-manager/backend
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt -r requirements-dev.txt
uvicorn app.main:app --host 0.0.0.0 --port 8080   # docs at /docs
python -m pytest -q                                # all tests must pass
ruff check app domain.py tests                     # must be clean
```

### Endpoints (`/api/v1`)

`auth/{signup,login,logout,me}` · `items` (CRUD) · `sales` (create/list) · `dashboard` ·
`settings` (owner) · `catalog/{products,categories,settings}` (public) · `cart` (per-user) ·
`health`. Every response uses the envelope; owner routes require a Bearer session token.

### Rules for new work (in addition to CLAUDE.md)

- Add features to the **FastAPI app**, not the legacy Flask/Node code.
- Parameterized SQL only. Validate input with Pydantic schemas. Return via the envelope.
- New owner data must stay owner-only; if a field should be public, add it explicitly to
  the `/catalog` projection — never widen the SELECT to include cost/location.
- Keep `domain.py` pure and framework-free; share rules from there.

---

## LEGACY REFERENCE (being retired) — original multi-server design

> Everything below documents the previous architecture for historical context during the
> migration. Do not build new features against it.

---

## 1. Project Identity

| Attribute | Value |
|-----------|-------|
| **App Name** | ARV Shop Manager |
| **Package/Bundle ID** | N/A (multi-repo system: Flask + Node.js + static site) |
| **Version** | 3.0 (per backup metadata) |
| **Description** | Complete shop management system with inventory, sales tracking, customer-facing catalog, WhatsApp checkout, and GitHub Pages publishing. Target audience: small retail shop owners who need offline-capable inventory management with a public product catalog. |

---

## 2. Tech Stack & Build Setup

### Languages & Frameworks
| Component | Stack |
|-----------|-------|
| **Shop Manager Backend** | Python 3, Flask 3.0, SQLite (WAL mode) |
| **Shop Manager Frontend** | Vanilla JS (ES6), HTML5, CSS3, Service Worker, IndexedDB, PWA |
| **Customer View (Node)** | Node.js (zero-dep HTTP server), Vanilla JS, HTML5, CSS3 |
| **Customer View Manager** | Node.js, Vanilla JS, HTML5, CSS3 |
| **GitHub Pages (Static)** | Static HTML/CSS/JS, Python generation scripts |
| **Database** | SQLite (two DBs: `shop.db` and `customer-view.db`) |

### Minimum Requirements
- Python 3.8+ (Flask, optional Pillow)
- Node.js 16+ (for Customer View & Manager)
- SQLite 3 (built-in)

### Build & Run Commands

| Task | Command |
|------|---------|
| **Start Shop Manager** | `cd shop-manager/backend && python3 app.py` → http://localhost:8080 (PIN: 1234) |
| **Import products → Customer View** | `cd customer-view/scripts && python3 import.py /path/to/shop.db` |
| **Generate static site** | `cd customer-view/scripts && python3 generate.py` |
| **Start Customer Site** | `cd customer-view/site && node server.js` → http://localhost:3000 |
| **Start View Manager** | `cd customer-view/manager && node server.js` → http://localhost:3001 |
| **Generate GitHub Pages catalog** | `cd git-pages && python3 scripts/sqlite_to_catalog.py ../shop-manager/backend/shop.db` |
| **Auto-backup to Google Drive** | `python3 shop-manager/scripts/auto_backup.py` |
| **Publish to GitHub Pages** | See `README.md` → "Publish to GitHub Pages" section |

### Dependencies

**Python (shop-manager/backend/requirements.txt)**
```
flask>=3.0
# Pillow (optional) - for image optimization; falls back to base64 storage if missing
```

**Node.js** — Zero dependencies (uses only stdlib: `http`, `fs`, `path`, `child_process`, `crypto`)

### Special Setup
- **Google Drive Backup**: Requires `google_api.py` script at `/root/.hermes/skills/productivity/google-workspace/scripts/google_api.py` and a configured Drive folder ID (`1Weo9kErWVbTvcscEURVrG6y3syeWm-mQ` by default). Config persisted at `/root/shop-manager/backups/.drive_config.json`.
- **PIN Lock**: Default PIN `1234`, changeable via UI. Stored in `localStorage` (Shop Manager) and browser cookies (Customer View).
- **WhatsApp Number**: Configured in settings (`917052696929` default).
- **Shop Location**: Google Maps link in settings.
- **HTTPS/localhost required** for Camera API (getUserMedia).

---

## 3. Architecture & Project Structure

### High-Level Architecture
```
┌─────────────────────────────────────────────────────────────────────────┐
│                         ARV Shop Manager                                │
├─────────────────┬───────────────────────────┬───────────────────────────┤
│  Shop Manager   │     Customer View         │      GitHub Pages         │
│  (Flask :8080)  │     (Node :3000/:3001)    │      (Static Site)        │
├─────────────────┼───────────────────────────┼───────────────────────────┤
│  - Inventory    │  - Public Catalog         │  - Static JSON data       │
│  - Sales        │  - Cart + WhatsApp        │  - Service Worker         │
│  - Camera/PWA   │  - Guest/Auth users       │  - Auto-regenerated       │
│  - Offline/IDB  │  - Device fingerprinting  │  - Free hosting           │
│  - GDrive Backup│  - View Manager (:3001)   │                           │
└────────┬────────┴──────────────┬─────────────┴────────────┬────────────┘
         │                       │                          │
         │    import.py          │                          │
         │  (sync + base64→file) │                          │
         ▼                       ▼                          ▼
    ┌─────────┐           ┌─────────────┐           ┌─────────────┐
    │shop.db  │  ──────►  │customer-view│  ──────►  │  products.  │
    │(source) │           │.db (target) │           │  json, etc. │
    └─────────┘           └─────────────┘           └─────────────┘
```

### Folder Structure
```
ARV-Shop-Manager/
├── shop-manager/
│   ├── backend/
│   │   ├── app.py                 # Flask API + image processing
│   │   ├── shop.db                # SQLite DB (created on first run)
│   │   ├── templates/index.html   # Manager UI (lock, tabs, camera, PWA)
│   │   ├── static/
│   │   │   ├── css/style.css      # Responsive, dark-ready styles
│   │   │   ├── css/lock-screen.css
│   │   │   ├── js/app.js          # Main frontend logic (1278 lines)
│   │   │   ├── js/localdb.js      # IndexedDB wrapper (567 lines)
│   │   │   ├── js/sw.js           # Service Worker (network-first)
│   │   │   └── manifest.json      # PWA manifest
│   │   └── static/images/items/   # Optimized product images
│   └── scripts/
│       ├── auto_backup.py         # GDrive backup (triggered on writes)
│       └── download_db.py         # Download live DB from Drive
│
├── customer-view/
│   ├── manager/
│   │   ├── server.js              # Node API + Auth + Cart + Manager UI
│   │   └── manager.html           # Manager dashboard (toggle visibility, etc.)
│   ├── site/
│   │   ├── index.html             # Customer catalog (search, cart, auth)
│   │   ├── server.js              # Static file server (port 3000)
│   │   ├── sw.js                  # Service Worker
│   │   ├── manifest.json          # PWA manifest
│   │   └── data/                  # products.json, categories.json, settings.json, version.json
│   ├── scripts/
│   │   ├── import.py              # shop.db → customer-view.db (base64→file)
│   │   └── generate.py            # customer-view.db → static site (site/data/)
│   ├── images/                    # Extracted product images (from base64)
│   └── db/                        # customer-view.db (created by import.py)
│
└── git-pages/
    ├── public/                    # Static site for gh-pages branch
    │   ├── index.html
    │   ├── app.js
    │   ├── styles.css
    │   ├── sw.js
    │   ├── manifest.json
    │   └── data/
    ├── server.js                  # Production server with DB watcher
    └── scripts/
        ├── sqlite_to_catalog.py   # shop.db → public/data/ (safe fields only)
        ├── sync_customer_catalog.py
        └── sqlite_to_json.py
```

### Navigation Pattern
- **Shop Manager**: Single-page app with drawer navigation (Dashboard, Inventory, Sales, Camera, Backup). Tab-based content switching via `switchTab()`.
- **Customer View**: Single-page catalog with modal product detail, slide-up cart, auth overlay. No router — all state in memory + localStorage.
- **View Manager**: Single-page dashboard with tables, toggle switches, inline editing. Fetch-based API calls.

### Data Flow
```
Shop Manager (source of truth)
    │
    ├─ POST /api/items, /api/sales → shop.db (SQLite WAL)
    │
    └─ Auto-backup thread → Google Drive (shop-manager-backup-*.json + shop-manager-live.db)
          │
          ▼
    import.py (manual or scheduled)
          │
          ├─ Reads shop.db (items table)
          ├─ Extracts base64 images → customer-view/images/item_<id>.jpg
          ├─ Computes discount_percent, stock_status
          └─ Upserts into customer-view.db (products, categories, settings)
                │
                ▼
    generate.py
          │
          ├─ Reads customer-view.db (visible products only)
          ├─ Copies images → customer-view/site/images/
          └─ Writes JSON → customer-view/site/data/ (products.json, categories.json, settings.json, version.json)
                │
                ▼
    GitHub Pages (git-pages/scripts/sqlite_to_catalog.py)
          │
          ├─ Reads shop.db directly (or downloaded live.db)
          ├─ Filters hidden products, applies view config
          ├─ Extracts base64 → public/images/
          └─ Writes JSON → public/data/ (products.json, categories.json, version.json, view-config.json)
```

---

## 4. Coding Standards & Conventions

### Naming Conventions
| Artifact | Convention |
|----------|------------|
| Python files | `snake_case.py` |
| JS files | `camelCase.js` or `lowercase.js` |
| HTML files | `kebab-case.html` or `index.html` |
| CSS classes | BEM-ish: `.block__element--modifier` (e.g., `.item-card`, `.qty-badge--zero`) |
| JS variables/functions | `camelCase` |
| Python variables/functions | `snake_case` |
| Database tables | `snake_case` plural (`items`, `daily_sales`, `products`, `user_cart`) |
| Database columns | `snake_case` |
| API endpoints | REST-ish: `/api/items`, `/api/items/<id>`, `/api/sales`, `/api/dashboard` |

### State Management
- **Shop Manager Frontend**: `appState` global object (lines 2-15 in `app.js`) holds items, sales, camera stream, online status, current tab. Persisted to IndexedDB via `LocalDB` module.
- **Customer View**: Module-level `let` variables (`P`, `S`, `localCart`, `user`) in IIFE. Cart persisted to `localStorage` (`cv_cart`). Auth token in cookie (`cv_token`).
- **View Manager**: Global `let` arrays (`allProducts`, `allCategories`, `settings`). No persistence — always fetches from API.

### UI Development Rules
- **Shop Manager**: Vanilla JS DOM manipulation. No framework. CSS custom properties for theming (`--primary`, `--success`, etc.). Responsive breakpoints at 768px and 480px.
- **Customer View**: Inline `<style>` block in HTML. Dark theme hardcoded (`--bg: #0f0f1a`, `--card: #1a1a2e`). Touch-friendly (44px min tap targets).
- **View Manager**: Inline `<style>` block. Dark GitHub-inspired theme (`#0d1117`, `#161b22`). Toggle switches for boolean fields.

### Error Handling Patterns
- **Flask**: Try/except in each endpoint. Returns `jsonify({'error': 'message'})` with appropriate HTTP status (400, 404, 500). Uses `mark_write()` to trigger auto-backup on successful writes.
- **Frontend (Shop Manager)**: `try/catch` around `fetch()`. On network error, falls back to IndexedDB (`loadItemsFromLocal()`). Offline actions queued in `localStorage.offlineActions`.
- **Customer View**: `try/catch` with user-facing `alert()` or inline error elements (`authError`). Graceful degradation (shows cached data if fetch fails).

### Logging Rules
- **Backend**: `print()` statements with prefixes: `[AUTO-BACKUP]`, `[Watcher]`, `[Cache]`, `[Publish]`. No structured logging library.
- **Frontend**: `console.log()` with emoji prefixes (`🚀`, `📦`, `✅`, `❌`). `console.error()` for failures.

### Custom Lint/Format
- No ESLint, Prettier, Ruff, or Black configured.
- Python: 4-space indent, line length ~100.
- JS: 2-space indent (in `app.js`), 4-space in `localdb.js`, mixed in HTML inline scripts.
- **Do not** run auto-formatters — they will churn unrelated files.

---

## 5. Testing Strategy

### Current State
- **No automated tests exist** in the repository.
- No test framework configured (no pytest, no Jest, no Vitest).
- Manual verification via:
  1. Start Shop Manager → `http://localhost:8080`
  2. Add item with camera → verify image appears
  3. Record sale → verify stock decrements
  4. Run `import.py` → verify `customer-view.db` populated
  5. Run `generate.py` → verify `site/data/products.json` created
  6. Start Customer View → `http://localhost:3000` → verify catalog, cart, WhatsApp
  7. Start View Manager → `http://localhost:3001` → toggle visibility, publish

### Required Coverage (if adding tests)
- **Unit**: Image processing (`process_image`), discount calculation, stock status logic, PIN validation.
- **Integration**: API endpoints (CRUD items, sales, dashboard), import/generate pipelines.
- **E2E**: Camera capture → add item → sync → catalog appearance → cart → WhatsApp checkout.

### Running Tests (Future)
```bash
# If pytest added
cd shop-manager/backend && python -m pytest

# If Jest added for frontend
cd customer-view/site && npm test
```

---

## 6. Agent Work Rules

### Adding a New Feature (Step-by-Step)

1. **Identify the layer(s) affected**:
   - **Backend API** → `shop-manager/backend/app.py` (new endpoint, DB schema change)
   - **Frontend UI** → `shop-manager/backend/templates/index.html` + `static/js/app.js`
   - **Local DB** → `shop-manager/backend/static/js/localdb.js` (new store/index)
   - **Customer View Sync** → `customer-view/scripts/import.py` (new field mapping)
   - **Static Generation** → `customer-view/scripts/generate.py` + `git-pages/scripts/sqlite_to_catalog.py`
   - **View Manager** → `customer-view/manager/server.js` + `manager.html` (if admin control needed)

2. **Database changes**:
   - Add column via `ALTER TABLE` in `init_db()` (Flask) or `init_db()` (import.py)
   - Add index if queried frequently
   - Update `LocalDB` schema version if IndexedDB store changes

3. **API endpoint**:
   - Follow existing pattern: `@app.route('/api/...', methods=['GET|POST|PUT|DELETE'])`
   - Validate input, return `jsonify({'error': ...})` on failure
   - Call `mark_write()` on successful mutations
   - Add `stock_status` computed field where items returned

4. **Frontend integration**:
   - Add UI in `index.html` (modal, form, tab, drawer item)
   - Wire events in `setupEventListeners()`
   - Add async function for API call (follow `addItem`, `saveEditItem` patterns)
   - Update `loadInitialData()` or tab switch to fetch new data
   - Sync to IndexedDB via `LocalDB.saveLocalItem()` or similar

5. **Customer View propagation**:
   - Add field to `products` table in `import.py` `init_db()`
   - Map in `import_shop_data()` SELECT and INSERT
   - Add to `generate.py` product dict output
   - Update `git-pages/scripts/sqlite_to_catalog.py` `extract_products()`

6. **View Manager (if admin control needed)**:
   - Add column to `products` table if persistent
   - Add toggle/input in `manager.html` table
   - Add API handler in `server.js` (`/api/products/<id>` POST)
   - Update `sync_customer_catalog.py` if it affects customer visibility

### Fixing a Bug

1. **Reproduce**: Use the running servers (8080, 3000, 3001). Check browser console + server logs.
2. **Root Cause**: Trace data flow: DB → API → Frontend State → Render. Check:
   - SQLite schema vs. query mismatch
   - Base64 vs. file image URL handling
   - `stock_status` computation (`quantity <= 0`)
   - Offline fallback logic (`loadItemsFromLocal` vs server)
   - Cache headers (`no-cache` everywhere — hard refresh expected)
3. **Fix**: Minimal change. Preserve fallback behavior (PIL optional, base64 fallback).
4. **Verify**:
   - Test online and offline (disable network in DevTools)
   - Test with and without Pillow installed
   - Verify GitHub Pages generation still works
   - Check auto-backup triggers (check `/tmp/app.log` or console)

### Branch Naming & Commits
- No enforced convention currently. Suggested:
  - `feature/<short-desc>` (e.g., `feature/low-stock-alerts`)
  - `fix/<short-desc>` (e.g., `fix/camera-ios-safari`)
  - `chore/<short-desc>` (e.g., `chore/update-dependencies`)
- Commit messages: Imperative, concise (`Add camera capture`, `Fix stock status sync`)

### PR/Review Process
- No automated CI. Manual verification required before merge.
- Checklist:
  - [ ] Shop Manager runs (`python3 app.py`)
  - [ ] Camera works (HTTPS/localhost)
  - [ ] Offline add → sync on reconnect
  - [ ] `import.py` succeeds
  - [ ] `generate.py` produces valid JSON
  - [ ] Customer View loads catalog, cart works
  - [ ] View Manager can toggle visibility
  - [ ] GitHub Pages generation works

### Never Do
| ❌ Forbidden | ✅ Do Instead |
|--------------|---------------|
| Hardcode strings in JS (use `data/settings.json` or constants) | Extract to settings or config |
| Skip error handling in `fetch()` | Always `.catch()` with fallback |
| Store secrets in code (API keys, passwords) | Use environment variables / config files |
| Modify `shop.db` schema without updating `import.py` | Keep both in sync |
| Remove `no-cache` headers | They ensure hard-refresh behavior |
| Assume Pillow is installed | Always guard with `HAS_PIL` and base64 fallback |
| Write to `shop.db` from Customer View | It's read-only for Customer View |
| Commit `shop.db` or `customer-view.db` | They're generated; `.gitignore` them |
| Use `localStorage` for large data (images) | Use IndexedDB (`LocalDB`) |

---

## 7. Key Files Quick Reference

| Purpose | File |
|---------|------|
| Flask API & DB init | `shop-manager/backend/app.py` |
| Main frontend logic | `shop-manager/backend/static/js/app.js` |
| IndexedDB wrapper | `shop-manager/backend/static/js/localdb.js` |
| Service Worker | `shop-manager/backend/static/js/sw.js` |
| UI template | `shop-manager/backend/templates/index.html` |
| Styles | `shop-manager/backend/static/css/style.css` |
| Customer import | `customer-view/scripts/import.py` |
| Static generation | `customer-view/scripts/generate.py` |
| Customer catalog UI | `customer-view/site/index.html` |
| View Manager API | `customer-view/manager/server.js` |
| View Manager UI | `customer-view/manager/manager.html` |
| GitHub Pages generator | `git-pages/scripts/sqlite_to_catalog.py` |
| Production server | `git-pages/server.js` |
| Auto-backup | `shop-manager/scripts/auto_backup.py` |
| Settings (customer) | `customer-view/site/data/settings.json` |
| Settings (GitHub Pages) | `git-pages/data/view-config.json` |

---

*Generated from codebase analysis. Keep this file updated as the project evolves.*