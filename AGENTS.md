# AGENTS.md — ARV Shop Manager

> **Document version:** 2026-07-02
> **Repo:** `singhvishalvikram/ARV-Shop-Manager`
> **Purpose:** This file is the canonical reference for every human developer and AI
> agent working in this repository. Read it before touching any file.

---

## Project Overview

ARV Shop Manager is a **single-shop inventory and catalog management platform** built
for small Indian retail shops. It has two distinct personas:

**Shop Manager (Admin)** — The shop owner uses a PIN-protected web app (Flask, port
8080) to add products with photos (camera or upload), track stock quantities, record
sales, and watch revenue metrics on a dashboard. Every write to the database
automatically triggers a backup to Google Drive.

**Customer (Storefront)** — Customers browse a PWA catalog (Node.js, port 3000 or
GitHub Pages) that shows product cards, prices, MRP with discount badges, stock
availability, and a WhatsApp-based checkout. A separate View Manager (port 3001) lets
the shop owner curate what appears on the customer site — toggling visibility, setting
featured flags, adding badges, and publishing to GitHub Pages in one click.

The platform works end-to-end for a **single shop** today. The next major milestones
are **multi-tenancy** (one platform, many shops) and an **Android Play Store app**
via TWA/Bubblewrap wrapping the existing PWA.

---

## Repository Structure

```
ARV-Shop-Manager/                          # Monorepo root
│
├── shop-manager/                          # Admin backend + Manager UI
│   ├── backend/                           # Flask (Python) app — port 8080
│   │   ├── app.py                         # Main Flask routes + image processing
│   │   ├── requirements.txt               # Python deps (flask>=3.0; Pillow optional)
│   │   ├── shop.db                        # SQLite DB — NOT committed (.gitignore)
│   │   ├── templates/
│   │   │   └── index.html                # Manager web UI (rendered by Flask)
│   │   └── static/
│   │       ├── js/
│   │       │   ├── app.js                # Manager frontend: PIN, camera, tabs, offline
│   │       │   └── sw.js                 # Shop Manager service worker
│   │       ├── css/
│   │       │   └── style.css             # Manager UI responsive dark styles
│   │       ├── manifest.json             # PWA manifest for Shop Manager (full icons)
│   │       └── images/items/             # Uploaded product images — NOT committed
│   └── scripts/
│       ├── auto_backup.py                # Google Drive backup (via hermes google_api)
│       └── download_db.py                # Download shop.db from Drive (read-only)
│
├── customer-view/                         # Customer catalog pipeline + servers
│   ├── manager/                           # View Manager — port 3001 (Node.js)
│   │   ├── server.js                     # Curate products, sync, publish — no npm deps
│   │   └── manager.html                  # View Manager web UI
│   ├── site/                              # Customer-facing static site — port 3000
│   │   ├── index.html                    # Customer catalog HTML
│   │   ├── server.js                     # Minimal Node.js static server
│   │   ├── manifest.json                 # PWA manifest for customer site
│   │   └── data/                          # Generated JSON — never hand-edit
│   │       ├── products.json
│   │       ├── categories.json
│   │       ├── settings.json
│   │       └── version.json
│   ├── scripts/
│   │   ├── import.py                     # shop.db → customer-view.db pipeline
│   │   ├── generate.py                   # customer-view.db → site/data/*.json
│   │   └── import.js                     # [LEGACY] earlier JS import attempt
│   └── db/                               # customer-view.db — NOT committed
│
├── git-pages/                             # GitHub Pages deployment assets
│   ├── index.html                         # Production customer catalog HTML
│   ├── app.js                             # Production frontend JS
│   ├── styles.css                         # Dark-theme CSS
│   ├── sw.js                              # Service worker (network-first strategy)
│   ├── manifest.json                      # PWA manifest (icons array currently [])
│   ├── server.js                          # Optional local production server
│   ├── admin.html                         # CEA admin UI (standalone variant)
│   ├── data/
│   │   ├── products.json                 # Generated — source of truth for PWA
│   │   ├── categories.json
│   │   ├── settings.json                 # Branding, WhatsApp, theme color, etc.
│   │   ├── version.json                  # Cache-bust timestamp + product count
│   │   └── view-config.json              # Alternate display settings config
│   ├── icons/                             # TODO: real app icons needed here
│   ├── images/                            # Product images for GitHub Pages
│   └── scripts/
│       ├── sqlite_to_catalog.py          # Direct DB → git-pages/data/ generator
│       ├── sqlite_to_json.py             # Simple JSON dump helper
│       └── sync_customer_catalog.py      # Sync via intermediate view-manager.db
│
├── .gitignore                             # Excludes *.db, backups/, .env, node_modules/
├── README.md                              # Quick-start guide
├── APP-AUDIT-AND-SELL-STRATEGY.md        # Detailed feature audit + SaaS roadmap
└── AGENTS.md                              # You are here
```

### Key File Decision Matrix

| File | Role | Edit Freely? |
|------|------|-------------|
| `shop-manager/backend/app.py` | Core Flask API | Yes — with tests |
| `shop-manager/backend/shop.db` | Live SQLite DB | **NEVER commit** |
| `customer-view/manager/server.js` | Publish pipeline | Yes — with care |
| `customer-view/scripts/import.py` | Data sync engine | Yes — with tests |
| `customer-view/scripts/generate.py` | Site generator | Yes — with tests |
| `git-pages/data/*.json` | Generated data files | **Never hand-edit** |
| `git-pages/sw.js` | Service worker | Yes — bump `CACHE_NAME` after changes |
| `shop-manager/scripts/auto_backup.py` | Drive backup | Yes — test offline first |
| `.gitignore` | Exclusion rules | Yes — do NOT remove `*.db` entry |

---

## Build & Development Commands

> **Note:** There is no `package.json` at the repo root. Node.js servers have
> **zero npm dependencies** — only built-in modules (`http`, `fs`, `path`,
> `child_process`, `crypto`). There is no bundler or transpiler. Python
> dependencies are minimal.

### 1. Shop Manager (Admin Backend)

```bash
cd shop-manager/backend

# Install Python dependencies
pip3 install flask>=3.0       # Required
pip3 install Pillow           # Optional — enables image optimization

# Start the server (creates shop.db on first run, default PIN: 1234)
python3 app.py
# → http://localhost:8080
```

### 2. Customer View — Import Pipeline

```bash
# Step 1: Import products from Shop Manager DB into Customer View DB
cd customer-view/scripts
python3 import.py /path/to/shop-manager/backend/shop.db

# Step 2: Generate static site files from Customer View DB
python3 generate.py
```

### 3. Customer View — Site Server

```bash
cd customer-view/site
node server.js
# → http://localhost:3000
```

### 4. View Manager (Product Curation + Publisher)

```bash
cd customer-view/manager
node server.js
# → http://localhost:3001
```

### 5. GitHub Pages Deployment (what the Publish button does)

```bash
# The View Manager /api/publish endpoint runs these commands internally:
python3 /root/customer-view/scripts/generate.py

cp -r /root/customer-view/site/* /root/Cinema123BW-Customer-Catalog/public/
cd /root/Cinema123BW-Customer-Catalog
git add -A
git commit -m "Publish $(date -Iseconds)"
git push origin gh-pages
# GitHub Pages deploys automatically in ~30 seconds
```

### 6. Direct DB → git-pages Pipeline (Alternative)

```bash
# Generate catalog JSON directly from shop.db into git-pages/data/
cd git-pages/scripts
python3 sqlite_to_catalog.py /path/to/shop-manager/backend/shop.db
```

### 7. Google Drive Backup

```bash
# Manual backup
python3 shop-manager/scripts/auto_backup.py

# Download DB from Drive (for migration or restore)
python3 shop-manager/scripts/download_db.py
```

> **TODO:** No `Makefile` or unified launcher script exists. Recommend adding
> `scripts/dev.sh` to start all three servers with a process manager.

> **TODO:** No CI/CD pipeline (no `.github/workflows/` directory). No test
> runner is configured. The only automated process is the backup triggered
> from inside `app.py` via a background thread on every write.

---

## Architecture Notes

### Component Map

```
┌──────────────────────────────────────────────────────────────────┐
│                      SHOP OWNER'S DEVICE                         │
│                                                                  │
│  Shop Manager PWA (Flask :8080)                                  │
│  ┌─────────────────────────────────────┐                        │
│  │ PIN lock → Dashboard → Inventory    │                        │
│  │          → Sales → Settings        │                        │
│  └──────────────┬──────────────────────┘                        │
│                 │ writes to                                      │
│                 ▼                                                │
│           shop.db (SQLite)                                       │
│                 │ auto-trigger (background thread)               │
│                 ▼                                                │
│           Google Drive (JSON backup + raw .db file)             │
└──────────────────────────────────────────────────────────────────┘
                 │
                 │ import.py (manual run or "Sync" button)
                 ▼
┌──────────────────────────────────────────────────────────────────┐
│                   CUSTOMER VIEW PIPELINE                         │
│                                                                  │
│  customer-view.db (SQLite)                                       │
│  ├─ products (visible, featured, badge, overrides, stock)       │
│  ├─ categories (visible, display_name, sort_order)              │
│  ├─ settings  (title, whatsapp, theme, currency …)             │
│  ├─ users + sessions (guest / phone+password auth)              │
│  └─ user_cart                                                    │
│                 │                                                │
│                 │ generate.py (manual or "Sync" button)         │
│                 ▼                                                │
│  site/data/  products.json                                       │
│              categories.json                                     │
│              settings.json                                       │
│              version.json  ← cache-bust signal                  │
└──────────────────┬───────────────────────────────────────────────┘
                   │
       ┌───────────┼────────────────┐
       │           │                │
       ▼           ▼                ▼
   LOCAL DEV   VIEW MANAGER    GITHUB PAGES
   :3000        :3001           (static CDN)
   node         node            singhvishalvikram
   server.js    server.js       .github.io/Cinema123BW
                    │
             Publish button
             git push → gh-pages
```

### Data Flow (Product Lifecycle)

1. **Owner adds product** → `POST /api/items` (Flask :8080)
   - Image decoded from base64, resized ≤400×400, saved as JPEG (if Pillow present)
   - Row inserted into `shop.db → items`
   - Background thread fires `auto_backup.py` → Google Drive (non-blocking)

2. **Owner curates** → Clicks "Sync" in View Manager (:3001)
   - `import.py` reads `shop.db`, upserts into `customer-view.db → products`
   - Resolves images: base64 → files, `/static/` paths → copied to `customer-view/images/`
   - Computes `discount_percent`, sets `stock_status` from `quantity`

3. **Generate static site** → `generate.py`
   - Reads only `visible=1` products from `customer-view.db`
   - Applies `title_override` / `description_override` fields
   - Writes `site/data/*.json`, copies images to `site/images/`

4. **Publish** → `POST /api/publish` (View Manager :3001)
   - Runs `generate.py`, copies files to `Cinema123BW-Customer-Catalog` repo
   - `git commit && git push` → GitHub Pages auto-deploys in ~30 s

5. **Customer browses** → fetches `products.json` from GitHub Pages CDN
   - Service worker caches with network-first strategy
   - `version.json` acts as cache-bust: new `v` value triggers fresh fetch
   - WhatsApp button pre-fills order enquiry message

### Auth Model

| Actor | Mechanism | Strength |
|-------|-----------|----------|
| Shop Manager | 4-digit PIN — client-side only (`localStorage`) | ⚠️ Weak (no server enforcement) |
| Customer Guest | Device fingerprint → DB row (`is_guest=1`) + 30-day token | Acceptable |
| Customer Registered | Phone + SHA-256(password+salt) + session token | Moderate |
| View Manager | No authentication on `/api/*` | ⚠️ Localhost only |

---

## Code Style & Conventions

### Python

- **Standard:** PEP 8 (loosely followed — no linter configured).
- **Docstrings:** Module-level triple-quote strings present on key files.
  Preserve all existing docstrings when editing.
- **Error handling:** `try/except Exception as e` with `print(...)` logging.
  Flask routes return `jsonify({'error': '...'}), 4xx` for client errors.
- **Paths:** Always use `os.path.join(os.path.dirname(__file__), ...)`.
  **Never hardcode `/root/...`** — this is existing technical debt, not a pattern
  to follow.
- **DB access:** Call `get_db()` for Flask routes. Always set `row_factory =
  sqlite3.Row`. Always close connections explicitly. Use `PRAGMA journal_mode=WAL`.
- **Imports:** stdlib first, then third-party. No `__future__` imports needed.

### JavaScript (Node.js servers + browser)

- **Standard:** ES6+ — `const`/`let`, arrow functions, template literals, `async/await`.
- **Zero frameworks, zero npm packages.** All servers use only Node.js built-ins.
  All frontends use Vanilla JS. This is intentional — do not add npm deps without
  explicit human approval.
- **Server pattern:** Raw `http.createServer`, manual routing by `pathname`,
  `parseBody()` helper for JSON bodies, `sendJson()` helper for responses.
- **SQLite from Node:** Servers shell out to Python (`runPython` / `dbQuery` helpers)
  instead of using a native binding — keeps zero-npm constraint.
- **Naming:** `camelCase` for JS identifiers, `kebab-case` for HTML IDs.

### HTML / CSS

- **Theme:** Dark mode. CSS custom properties pattern (`--color-*`).
- **Responsive:** Mobile-first. Product grid driven by `products_per_row` from
  `settings.json` via CSS Grid.
- **No preprocessors:** Raw CSS3. No SASS, LESS, PostCSS.

### Commit Messages

> **TODO:** No formal convention documented. Observed pattern in the publish
> script: `"Publish 2026-06-26T10-50-21"`. Recommended: adopt Conventional
> Commits (`feat:`, `fix:`, `chore:`, `docs:`).

### File / Folder Naming

| Layer | Convention | Example |
|-------|------------|---------|
| Python source files | `snake_case.py` | `import.py`, `auto_backup.py` |
| JS files | `lowercase.js` | `app.js`, `server.js` |
| HTML files | `lowercase.html` | `index.html`, `manager.html` |
| DB tables | `snake_case` | `daily_sales`, `user_cart` |
| REST endpoints | `/api/kebab-case` | `/api/backup/status` |
| JSON data files | `kebab-case.json` | `products.json`, `view-config.json` |

---

## Testing Strategy

> **TODO:** No automated tests exist. The items below describe current manual
> verification and the recommended path to add tests.

### Manual Test Checklist (Current Approach)

```bash
# 1. Verify Flask backend starts and DB initializes
cd shop-manager/backend && python3 app.py
curl http://localhost:8080/api/dashboard

# 2. Test item creation
curl -X POST http://localhost:8080/api/items \
  -H "Content-Type: application/json" \
  -d '{"name":"Test","type":"Widget","price":100,"quantity":10}'

# 3. Verify import + generate pipeline
cd customer-view/scripts
python3 import.py ../../shop-manager/backend/shop.db
python3 generate.py
python3 -m json.tool ../site/data/products.json | head -40

# 4. Check service worker version bump after any SW change
grep "CACHE_NAME" shop-manager/backend/static/js/sw.js
grep "CACHE_NAME" customer-view/site/sw.js  # if present
grep "CACHE_NAME" git-pages/sw.js
```

### Recommended Tests to Add First

1. **`shop-manager/backend/test_app.py`** — pytest for Flask API:
   CRUD items, sales recording, stock-status computation, dashboard stats.
2. **`customer-view/scripts/test_pipeline.py`** — fixture SQLite DB → import
   → generate → validate JSON structure and field safety (no `purchase_cost`
   or `quantity` in output).
3. **`git-pages/scripts/test_catalog.py`** — test `sqlite_to_catalog.py`
   schema validation, discount calculation, base64 image extraction.

### CI/CD

> **TODO:** No `.github/workflows/` directory. Recommended:
> - On push to `main`: `python -m py_compile` all Python files + future pytest.
> - Separate workflow for APK build (Phase 4 of roadmap).

---

## Security & Compliance

### Sensitive Files

| Item | Gitignore? | Risk |
|------|-----------|------|
| `shop.db` | ✅ yes | Inventory + sales data |
| `customer-view/db/customer-view.db` | ✅ yes | User auth data (phone, hashes) |
| `backups/` | ✅ yes | Full DB exports |
| `.env` | ✅ yes | — |
| Google Drive folder ID | ⚠️ hardcoded in `app.py` + `auto_backup.py` | Low — not secret, but fragile |
| `AUTH_SECRET` salt | ⚠️ hardcoded in `manager/server.js` | Medium — rotate before public deploy |
| Manager PIN (`1234` default) | ⚠️ client-side only | High — no server enforcement |
| GitHub push credentials | ✅ system git config | — |

### What Must Never Be Committed

```
*.db
*.sqlite3
backups/
shop-manager/backend/static/images/items/
customer-view/images/
.env
*.bak
*.backup
*.bak2
```

### Known Auth Gaps

- **Shop Manager PIN** is validated in `app.js` client-side. The Flask backend
  has **no auth middleware** — `/api/*` routes are open to anyone who can reach
  port 8080. Intended for localhost only. Do not expose port 8080 to the internet
  without adding `X-PIN` header validation server-side.
- **View Manager** (port 3001) `/api/*` routes are also unauthenticated.
- **Customer-view sessions** use SHA-256 with a hardcoded salt (`AUTH_SECRET`).
  Replace with `bcrypt` before public launch.
- **PIL/Pillow** should be treated as a required dependency, not optional.
  Without it, images are stored as base64 data URLs directly in the DB, which
  causes the SQLite file to bloat rapidly.

---

## Agent Guardrails

These rules apply to every AI agent, automated script, or developer tool that
operates on this repository.

### 🔴 NEVER DO

- Commit any `.db` file (SQLite DBs contain real inventory and user data)
- Commit files under `backups/`, `static/images/items/`, or `customer-view/images/`
- Remove or weaken any `.gitignore` entry that protects databases or backups
- Hardcode new absolute paths like `/root/...` — they break outside the server
- Hardcode any secret (API key, token, password) in source files
- Run `git push` to any branch without explicit human approval
- Hand-edit `data/products.json`, `data/categories.json`, or `data/settings.json`
  — these are generated; regenerate via the pipeline scripts instead
- Change a SQLite table schema without providing a companion migration script
- Edit files ending in `.bak`, `.backup`, `.bak2` — they are historical snapshots
- Add npm packages to the Node.js servers without explicit human approval
  (the zero-dependency design is intentional)

### 🟡 PROCEED WITH EXTRA CARE

- **`app.py` Flask routes** — changes to existing endpoints can break `import.py`
  or the frontend. Run the manual test checklist after any change.
- **`import.py` and `generate.py`** — these touch every customer-visible product.
  Test with a fixture DB before deploying.
- **`manager/server.js` `/api/publish`** — runs `git push`. An error here leaves
  the live site out of sync.
- **`sw.js` files** — bump the `CACHE_NAME` constant whenever cached assets change,
  or stale content will be served to existing users.
- **`settings.json`** — changes propagate immediately to the live PWA. Validate JSON
  structure before writing.
- **`auto_backup.py`** — calls `hermes/google_api.py` which is external to this repo.
  Test with `python3 auto_backup.py --test` before deploying changes.
- **DB schema changes** — pair every `ALTER TABLE` with a migration script and test
  against existing data.

### 🟢 SAFE TO EDIT

- All `*.md` documentation files
- `templates/index.html`, `manager.html`, `customer-view/site/index.html` — UI HTML
  (verify visually in browser)
- `static/css/style.css`, `static/js/app.js` — frontend styling and logic
  (test in browser; verify PWA offline mode)
- `git-pages/scripts/*.py` — catalog generators (test against a copy of `shop.db`)
- New files added to `shop-manager/backend/` or `customer-view/scripts/` — safe
  as long as they don't alter existing DB schema or API contracts

### Human Review Required

| Trigger | Action |
|---------|--------|
| Any DB schema change | Human review before merge |
| Any API endpoint signature change | Human review |
| Any `git push` command | Human must explicitly approve |
| After any automated edit | Run manual test checklist |

---

## Extensibility Hooks

Clean places to add features without breaking existing behaviour.

### New Product Fields

1. `ALTER TABLE items ADD COLUMN <field>` in `app.py → init_db()` (write migration)
2. Expose in `get_items()` and `update_item()` in `app.py`
3. Add to `CUSTOMER_FIELDS` in `import.py` if customer-visible
4. Add to the `SELECT` query in `generate.py`
5. Render in `index.html` / `app.js`

### New Flask API Endpoints

- Add `@app.route('/api/...')` in `app.py`
- Call `mark_write()` on any endpoint that modifies data (triggers auto-backup)
- Return `jsonify({...}), <status>` — follow existing error shape

### New View Manager API

- Add handler inside the `if (pathname.startsWith('/api/'))` block in
  `customer-view/manager/server.js`
- Use `dbQuery()` / `dbExecute()` helpers (Python shell-out pattern)
- No npm packages — only Node.js built-ins

### Mobile App (Read) Integration Point

The static JSON API on GitHub Pages is the cleanest mobile integration surface —
no backend changes needed for a read-only client:

```
GET https://singhvishalvikram.github.io/Cinema123BW/data/products.json
GET https://singhvishalvikram.github.io/Cinema123BW/data/categories.json
GET https://singhvishalvikram.github.io/Cinema123BW/data/settings.json
GET https://singhvishalvikram.github.io/Cinema123BW/data/version.json
```

For write operations (cart, auth), the mobile app calls the View Manager
Auth/Cart API (`/api/auth/*`, `/api/cart` on port 3001).

### Settings-Driven Customization

`settings.json` (backed by `customer-view.db → settings` table) is the intended
extension point for white-label and per-shop config. Currently supported keys:

```
app_title, app_subtitle, whatsapp_number, shop_location,
theme_color, footer_text, currency_symbol,
show_search, show_category_filter, show_discount_badges,
show_mrp, show_description, show_images, show_location,
products_per_row, max_products
```

Add new display toggles here (new `key/value` rows) rather than hardcoding in HTML.

---

## Current State & Roadmap

### What Works Today ✅

| Feature | Location |
|---------|----------|
| Product CRUD with camera/gallery image | Flask `:8080` |
| Sales recording + stock auto-deduction | Flask `:8080` |
| Dashboard stats (revenue, stock value, type breakdown) | Flask `:8080` |
| Google Drive auto-backup on every write | `auto_backup.py` + threading |
| Import pipeline: `shop.db` → `customer-view.db` | `import.py` |
| Static site generation from `customer-view.db` | `generate.py` |
| Customer PWA: search, category filter, cart, WhatsApp checkout | `site/index.html` |
| Product curation (visibility, featured, badge) | View Manager `:3001` |
| One-click Sync + Publish to GitHub Pages | View Manager `:3001` |
| Guest + registered customer auth | View Manager `:3001` |
| Service worker offline caching (network-first) | `sw.js` |
| PWA manifest (installable as app) | `manifest.json` |
| Out-of-stock badge flow end-to-end | All layers |
| Discount badge (MRP vs selling price) | `import.py` + `index.html` |

### Partially Implemented ⚠️

| Item | Gap |
|------|-----|
| CEA (Client-End App) | Present in `git-pages/` as `admin.html`; runs on `:8081` but not wired into the main pipeline |
| `sync_customer_catalog.py` | Alternative sync script using a third `view-manager.db` that no startup step creates |
| `view-config.json` | File exists in `git-pages/data/` but active pipeline uses the `settings` DB table instead |
| Shop Manager offline queue | Documented in `README.md` but reliability not verified end-to-end |

### Known Gaps & Technical Debt 🔴

1. **Hardcoded absolute paths** — `/root/shop-manager/...` and
   `/root/customer-view/...` appear in `auto_backup.py`, `generate.py`,
   `manager/server.js`. These break on any machine where the repo is not at
   `/root/`. Use `__file__`-relative paths everywhere.

2. **No server-side auth on Flask or View Manager** — all `/api/*` routes are
   open. Localhost only. Not safe to expose to the internet.

3. **Single-tenant only** — one `shop.db`, one `customer-view.db`, one GitHub
   Pages repo. Full refactor needed for multi-shop.

4. **`manifest.json` icons array is empty** in `git-pages/manifest.json`
   (`"icons": []`). PWA install prompt fails or shows a blank icon on Android.
   TWA build will also fail without real icons.

5. **`AUTH_SECRET` hardcoded** in `manager/server.js`. Must become an
   environment variable before any public deployment.

6. **No pagination** — `GET /api/items` returns all rows with no limit.
   Will degrade with 500+ products.

7. **No CI/CD pipeline** — no `.github/workflows/` directory.

8. **Backup depends on `hermes` external tool** — `auto_backup.py` calls
   `python3 /root/.hermes/skills/.../google_api.py`. This tool is not in
   this repo and will not exist on a fresh machine.

---

### Ordered Roadmap

#### Phase 1 — Stabilize (Immediate Priority)

- [ ] Replace all `/root/...` absolute paths with `__file__`-relative paths
- [ ] Add real PNG icons (192×192, 512×512) to `git-pages/manifest.json`
- [ ] Move `AUTH_SECRET` to `.env` / environment variable
- [ ] Add server-side PIN validation to Flask (e.g., `X-Manager-PIN` header)
- [ ] Write pytest suite for Flask API (`shop-manager/backend/test_app.py`)
- [ ] Document the `hermes/google_api.py` external dependency clearly

#### Phase 2 — Multi-Tenant Foundation (Weeks 1–2)

- [ ] Add `shops` table; scope all DB paths to `data/shops/<shop_id>/`
- [ ] Middleware to identify shop from subdomain or URL path
- [ ] `POST /api/signup` — provisions new shop DB + default settings
- [ ] Parameterize `import.py` and `generate.py` to accept `--shop-id`
- [ ] Parameterize publish script to target per-shop GitHub repo

#### Phase 3 — Self-Serve Onboarding (Weeks 2–3)

- [ ] Landing page at `/` — "Create your shop in 5 minutes"
- [ ] Auto-provisioning on signup (DB, defaults, GitHub repo via API)
- [ ] White-label: replace every `"ARV ENTERPRISES"` literal with
      `settings.app_title`

#### Phase 4 — Android App (Weeks 4–5)

- [ ] Add real icons + host `assetlinks.json` to enable TWA verification
- [ ] Use Bubblewrap to generate TWA shell targeting the GitHub Pages PWA
- [ ] GitHub Actions workflow: on-demand APK build → artifact upload
- [ ] APK download link in View Manager UI

#### Phase 5 — Billing (Weeks 5–6)

- [ ] Razorpay integration (`subscriptions` table)
- [ ] Feature gating: free (50 products) vs pro (unlimited + custom domain)

#### Phase 6 — Admin Dashboard (Week 6–7)

- [ ] Super-admin view: all shops, plans, MRR, churn
- [ ] Suspend / activate / broadcast to all shop owners

---

## Mobile App Extension Plan

> **Status update (2026-07-02):** A native Java Android customer app now exists at
> `mobile/customer-app/` — see `MOBILE-ARCHITECTURE.md` for the architecture decision
> (native app chosen over the TWA route below), the 20-phase plan, and build commands.
> The TWA notes below are retained as the fallback option.

### Recommended Stack: TWA (Trusted Web Activity) via Bubblewrap

The customer-facing PWA already has `manifest.json` and a service worker. A TWA
wraps it in a native Android shell with **zero frontend code changes**.

**Why TWA over alternatives:**

| Option | Assessment |
|--------|-----------|
| **TWA (recommended)** | Reuses 100% of existing PWA code. Zero JS changes. Play Store presence. Auto-updates with PWA. |
| React Native / Flutter | Requires full frontend rewrite — weeks of work, no new functionality for customers |
| Capacitor (Ionic) | Heavier build pipeline, npm ecosystem, ARM64 APK issues noted in audit doc |
| Native Android | No mobile native expertise; overkill for current feature set |

**TWA cons:** Requires HTTPS (GitHub Pages provides this ✅) and
`assetlinks.json` hosted at `https://<domain>/.well-known/assetlinks.json`.

---

### Step-by-Step Implementation

#### Step 1 — Fix PWA Manifest Icons (1–2 days)

The current `git-pages/manifest.json` has `"icons": []`. TWA build will fail.

```json
// git-pages/manifest.json — add real icons
{
  "name": "ARV ENTERPRISES",
  "short_name": "ARV",
  "start_url": ".",
  "display": "standalone",
  "background_color": "#0f0f1a",
  "theme_color": "#6366f1",
  "icons": [
    { "src": "/icons/icon-192.png", "sizes": "192x192", "type": "image/png" },
    { "src": "/icons/icon-512.png", "sizes": "512x512", "type": "image/png",
      "purpose": "any maskable" }
  ]
}
```

Place `icon-192.png` and `icon-512.png` in `git-pages/icons/`, commit, and
publish. Verify with Chrome DevTools → Application → Manifest.

#### Step 2 — Digital Asset Links (1 day)

Create `git-pages/.well-known/assetlinks.json`:

```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "com.arvshop.cinema123bw",
    "sha256_cert_fingerprints": ["<YOUR_KEYSTORE_SHA256_FINGERPRINT>"]
  }
}]
```

Commit and push so GitHub Pages serves it at:
`https://singhvishalvikram.github.io/Cinema123BW/.well-known/assetlinks.json`

#### Step 3 — TWA Shell with Bubblewrap (1–2 days)

```bash
# One-time: install Bubblewrap CLI
npm install -g @bubblewrap/cli

# Initialise TWA project from the live PWA manifest
bubblewrap init \
  --manifest https://singhvishalvikram.github.io/Cinema123BW/manifest.json

# Review twa-manifest.json — confirm:
#   packageId: com.arvshop.cinema123bw
#   host: singhvishalvikram.github.io
#   startUrl: /Cinema123BW/

# Build signed APK (requires Java / Android SDK)
bubblewrap build
# Output: app/build/outputs/apk/release/app-release-signed.apk
```

#### Step 4 — Test on Device (1 day)

```bash
# Sideload to connected Android device
bubblewrap install

# Verify:
# - Browser address bar disappears (TWA verified)
# - Service worker caches products offline
# - WhatsApp button opens WhatsApp app
# - Products load from GitHub Pages CDN
# - version.json polling triggers cache refresh
```

#### Step 5 — Play Store Submission (2–3 days)

- One-time $25 Google Play developer account fee
- Create store listing: icon, screenshots (5+), short/full description
- Set content rating: `Everyone`
- Upload signed APK or AAB → submit for review (~1–3 business days)

#### Step 6 — Optional CI/CD APK Build (Week 5)

```yaml
# .github/workflows/build-apk.yml
on:
  push:
    branches: [main]
    paths: ['git-pages/**']
jobs:
  build-twa:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with: { node-version: '18' }
      - uses: actions/setup-java@v3
        with: { distribution: temurin, java-version: '17' }
      - run: npm install -g @bubblewrap/cli
      - run: bubblewrap build
      - uses: actions/upload-artifact@v3
        with:
          name: customer-apk
          path: app/build/outputs/apk/release/*.apk
```

---

### Data & API Compatibility — Mobile vs Web

No backend changes are needed for the initial mobile release:

| Customer Need | How Mobile App Gets It |
|---------------|------------------------|
| Product catalog | `GET /data/products.json` (GitHub Pages static) |
| Categories | `GET /data/categories.json` (static) |
| Branding / theme | `GET /data/settings.json` (static) |
| Cache busting | Poll `version.json` on app foreground; if `v` changed → fetch fresh data |
| Cart | `localStorage` (same as PWA) or `POST /api/cart` (View Manager `:3001`) |
| Auth | `POST /api/auth/guest` or `/api/auth/login` (View Manager `:3001`) |
| Orders | WhatsApp deep link — already implemented in `index.html` |

> **Assumption:** The mobile app targets the **customer catalog only** (browse +
> cart + WhatsApp enquiry). The Shop Manager admin UI is not included. If the
> owner wants to manage stock from a phone, the Shop Manager PWA already renders
> on mobile browsers — it can be installed separately.

### Version Compatibility

- `version.json → v` (Unix timestamp) is the only coordination signal needed.
- Service worker handles cache invalidation automatically.
- No API versioning is required for static JSON files.
- For Cart/Auth API: treat it as internal v1. Document any breaking changes in
  `AGENTS.md` before shipping a new version.

---

## Further Reading

| Document | Location | Notes |
|----------|----------|-------|
| Quick-start | `README.md` | Server startup commands |
| Full feature audit | `APP-AUDIT-AND-SELL-STRATEGY.md` | API tables, DB schema, SaaS strategy |
| Shop Manager deep-dive | `shop-manager/README.md` | Camera, offline, PWA details |
| Live customer catalog | `https://singhvishalvikram.github.io/Cinema123BW` | Production site |
| Source repo | `https://github.com/singhvishalvikram/ARV-Shop-Manager` | This repo |
| Drive backup folder | `1Weo9kErWVbTvcscEURVrG6y3syeWm-mQ` | Google Drive folder ID |

> **TODO:** No OpenAPI/Swagger spec, no deployment runbook, and no environment
> variable reference document exist. These should be created before onboarding
> additional developers or going multi-tenant.

---

*End of AGENTS.md. Update this file whenever the architecture, API contracts,
or deployment workflow change.*
