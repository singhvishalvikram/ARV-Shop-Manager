# ARV Shop Manager — Complete App Audit & Multi-Shop Sell Strategy

> Generated: June 2026
> Repo: https://github.com/singhvishalvikram/ARV-Shop-Manager

---

## TABLE OF CONTENTS

1. [Working Features Inventory](#1-working-features-inventory)
2. [Tech Stack & Tools](#2-tech-stack--tools)
3. [Database Schema](#3-database-schema)
4. [API Endpoints](#4-api-endpoints)
5. [Frontend Features](#5-frontend-features)
6. [Sync & Publish Pipeline](#6-sync--publish-pipeline)
7. [What Needs Work for Multi-Sale](#7-what-needs-work-for-multi-sale)
8. [Multi-Shop Sell Strategy (Brainstorm)](#8-multi-shop-sell-strategy-brainstorm)
9. [Android Deployment Architecture](#9-android-deployment-architecture)
10. [Action Plan — Make It Sale-Ready](#10-action-plan--make-it-sale-ready)

---

## 1. WORKING FEATURES INVENTORY

### A. Shop Manager (Flask Backend + Web UI) — Port 8080

| Feature | Status | Location |
|---------|--------|----------|
| PIN lock screen (4-digit, changeable) | WORKING | `app.js` lock screen logic |
| Dashboard with stats | WORKING | `/api/dashboard` — total items, stock value, today's revenue, type breakdown |
| Add product with image (camera/gallery) | WORKING | `POST /api/items` + `process_image()` |
| Edit product (all fields) | WORKING | `PUT /api/items/<id>` |
| Delete product | WORKING | `DELETE /api/items/<id>` |
| Search products (name/type/description) | WORKING | `GET /api/items?search=` |
| Record sale (auto-reduces stock) | WORKING | `POST /api/sales` |
| Sales history with date filter | WORKING | `GET /api/sales?date=` |
| Stock status (in_stock / out_of_stock) | WORKING | computed field, qty<=0 → out_of_stock |
| Image processing (resize to 400x400, JPEG q75) | WORKING | `process_image()` in app.py |
| Google Drive auto-backup on every write | WORKING | `trigger_auto_backup()` via threading |
| Manual backup trigger | WORKING | `POST /api/backup` |
| Backup status & history | WORKING | `GET /api/backup/status` |
| Download DB from Drive (read-only remote) | WORKING | `download_db.py` |
| Daily revenue tracking | WORKING | `today_revenue` in sales API |
| Total revenue tracking | WORKING | `total_revenue` in sales API |
| Stock value at cost/MRP/price | WORKING | dashboard stats |
| Product location field | WORKING | per-item location |
| MRP + auto discount calculation | WORKING | mrp default = price*1.2 |
| Purchase cost tracking | WORKING | purchase_cost field |

### B. Customer View (Manager :3001 + Site :3000)

| Feature | Status | Location |
|---------|--------|----------|
| Import products from Shop Manager DB | WORKING | `import.py` |
| Product visibility toggle | WORKING | manager `/api/products/<id>` POST |
| Featured product flag | WORKING | manager POST |
| Custom badge per product | WORKING | manager POST |
| Title/description override | WORKING | manager POST |
| Category management (visibility, display name) | WORKING | manager `/api/categories` |
| App settings (title, subtitle, WhatsApp, theme color, currency, etc.) | WORKING | manager `/api/settings` |
| Static site generation | WORKING | `generate.py` |
| Service Worker (offline caching) | WORKING | `sw.js` |
| PWA manifest | WORKING | `manifest.json` |
| Product search on frontend | WORKING | `index.html` JS |
| Category filter chips | WORKING | `index.html` JS |
| Out-of-stock badge (orange, dimmed card, hides cart) | WORKING | CSS + JS |
| Discount badge (MRP vs selling price) | WORKING | CSS + JS |
| Publish to GitHub Pages | WORKING | manager `/api/publish` |
| One-click sync + publish | WORKING | manager `/api/sync` |

### C. Customer Auth & Cart (on Manager :3001)

| Feature | Status | Location |
|---------|--------|----------|
| Guest auto-login (device fingerprint) | WORKING | `/api/auth/guest` |
| Phone + password signup | WORKING | `/api/auth/signup` |
| Phone + password login | WORKING | `/api/auth/login` |
| Session token (30-day expiry) | WORKING | `sessions` table |
| Add to cart (with qty) | WORKING | `POST /api/cart` |
| View cart | WORKING | `GET /api/cart` |
| Remove from cart | WORKING | `DELETE /api/cart/<id>` |
| Logout | WORKING | `/api/auth/logout` |

### D. Client-End App (CEA — Alternative lightweight variant) — Port 8081

| Feature | Status | Location |
|---------|--------|----------|
| Single-file Python HTTP server | WORKING | `server.py` |
| Product CRUD (admin PIN auth) | WORKING | `/api/admin/products` |
| Image upload to disk | WORKING | `/api/admin/upload` |
| Settings management | WORKING | `/api/admin/settings` |
| Stats dashboard | WORKING | `/api/admin/stats` |
| Backup (JSON export) | WORKING | `/api/admin/backup` |
| Import (JSON restore) | WORKING | `/api/admin/import` |
| Public product listing with search/sort/filter | WORKING | `/api/products` |
| Sort by price/name/discount/newest | WORKING | query param `sort=` |
| Featured products filter | WORKING | query param `featured=1` |
| Category filter | WORKING | query param `category=` |
| Session-based auth (1hr timeout) | WORKING | in-memory sessions |
| Security headers (XSS, frame, content-type) | WORKING | response headers |

### E. GitHub Pages Deployment (`git-pages/` and `Cinema123BW-Customer-Catalog/`)

| Feature | Status | Location |
|---------|--------|----------|
| Static catalog generation | WORKING | `sync_customer_catalog.py` + `sqlite_to_catalog.py` |
| products.json output | WORKING | `data/products.json` |
| categories.json output | WORKING | `data/categories.json` |
| settings.json output | WORKING | `data/settings.json` |
| version.json (timestamp + count) | WORKING | `data/version.json` |
| Git commit + push | WORKING | manager `/api/publish` |
| gh-pages branch deployment | WORKING | `git-pages/` dir |

---

## 2. TECH STACK & TOOLS

### Backend
| Tool | Version | Purpose |
|------|---------|---------|
| Python 3.x | 3.10+ | Main backend language |
| Flask | 2.x | Shop Manager web framework |
| Node.js | 14+ | Customer View servers (no npm deps) |
| SQLite3 | built-in | Database engine |
| PIL/Pillow | optional | Image optimization (400x400, JPEG) |

### Frontend
| Tool | Purpose |
|------|---------|
| Vanilla JS | All frontend logic |
| CSS3 (variables) | Dark theme, responsive grids |
| Service Worker | Offline caching + cache busting |
| PWA manifest | Installable as app |
| localStorage | PIN storage, device fingerprint, cart (client-side) |
| Camera API (getUserMedia) | Product photo capture |
| No frameworks | Zero JS dependencies |

### Infrastructure
| Tool | Purpose |
|------|---------|
| GitHub Pages | Free static site hosting |
| Google Drive API | DB backup + raw DB upload |
| GitHub Actions | Pages deployment on push |
| git gh-pages branch | Static catalog publishing |
| Threading (Python) | Non-blocking auto-backup |

### Data Flow
```
Shop Manager (Flask :8080)
    ↓ (writes to shop.db)
    ↓ (auto-backup → Google Drive)
    ↓
import.py (reads shop.db → writes customer-view.db)
    ↓
generate.py (reads customer-view.db → generates static site/)
    ↓
Manager :3001 (publish → git push → GitHub Pages lives here)
    ↓
Customer Site :3000 (serves static site/)
```

---

## 3. DATABASE SCHEMA

### Shop Manager DB (`shop.db`)

```
items
  id INTEGER PK
  name TEXT NOT NULL
  type TEXT
  description TEXT
  price REAL (selling price)
  mrp REAL (maximum retail price)
  purchase_cost REAL (cost price)
  image_url TEXT (path or data: URL)
  location TEXT (physical shelf/location)
  quantity INTEGER
 _created_at TEXT
  updated_at TEXT

daily_sales
  id INTEGER PK
  item_id INTEGER FK → items.id
  quantity_sold INTEGER
  sale_price REAL
  sale_date TEXT
  description TEXT
```

### Customer View DB (`customer-view.db`)

```
products
  id INTEGER PK (mirrors items.id)
  name TEXT, type TEXT, description TEXT
  price REAL, mrp REAL, discount_percent INTEGER
  image_url TEXT, visible INTEGER, featured INTEGER
  badge TEXT, sort_order INTEGER
  title_override TEXT, description_override TEXT
  source_updated_at TEXT, stock_status TEXT
  imported_at TEXT, updated_at TEXT

categories
  id INTEGER PK AUTOINCREMENT
  name TEXT UNIQUE, display_name TEXT
  visible INTEGER, sort_order INTEGER

settings
  key TEXT PRIMARY KEY, value TEXT

sync_log
  id INTEGER PK, timestamp TEXT, action TEXT
  products_added/updated/removed INTEGER, details TEXT

users
  id INTEGER PK, phone TEXT UNIQUE, name TEXT
  password_hash TEXT, is_guest INTEGER
  created_at TEXT, last_login TEXT

user_cart
  id INTEGER PK, user_id FK, product_id FK
  qty INTEGER, added_at TEXT
  UNIQUE(user_id, product_id)

sessions
  id INTEGER PK, user_id FK
  token TEXT UNIQUE, expires TEXT
```

### CEA DB (`cea.db`)

```
products
  id INTEGER PK, name TEXT, description TEXT
  category TEXT, brand TEXT
  mrp REAL, selling_price REAL, discount_pct REAL
  stock_status TEXT, quantity INTEGER
  image_url TEXT, featured INTEGER
  created_at TEXT, updated_at TEXT

settings
  key TEXT PRIMARY KEY, value TEXT
  (keys: shop_name, whatsapp, maps_url, currency, admin_pin_hash)

backup_log
  id INTEGER PK, filename TEXT, created_at TEXT
```

---

## 4. API ENDPOINTS

### Shop Manager (`app.py`, port 8080)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/` | PIN | Manager UI (index.html) |
| GET | `/api/items` | PIN | List all items (?search=) |
| GET | `/api/items/<id>` | PIN | Get single item |
| POST | `/api/items` | PIN | Create item (JSON + image_base64) |
| PUT | `/api/items/<id>` | PIN | Update item |
| DELETE | `/api/items/<id>` | PIN | Delete item |
| POST | `/api/sales` | PIN | Record sale (reduces stock) |
| GET | `/api/sales` | PIN | Sales history (?date=YYYY-MM-DD) |
| GET | `/api/dashboard` | PIN | Stats: items, revenue, stock value |
| POST | `/api/backup` | PIN | Manual Google Drive backup |
| GET | `/api/backup/status` | PIN | List recent backups |

### Customer View Manager (`server.js`, port 3001)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/` | none | Manager HTML |
| GET | `/api/dashboard` | none | Stats (products, categories, sync) |
| GET | `/api/products` | none | List all products |
| POST | `/api/products/<id>` | none | Update visibility/featured/badge |
| GET | `/api/settings` | none | Get all settings |
| POST | `/api/settings` | none | Update settings |
| GET | `/api/categories` | none | List categories |
| POST | `/api/categories` | none | Update category |
| POST | `/api/sync` | none | Run import.py + generate.py |
| POST | `/api/publish` | none | Generate + git push to Pages |
| POST | `/api/auth/guest` | none | Create/continue guest session |
| POST | `/api/auth/signup` | none | Register with phone+password |
| POST | `/api/auth/login` | none | Login with phone+password |
| POST | `/api/auth/logout` | token | End session |
| GET | `/api/auth/me` | cookie | Get current user |
| GET | `/api/cart` | token | Get cart items |
| POST | `/api/cart` | token | Add to cart |
| DELETE | `/api/cart/<id>` | token | Remove from cart |

### Customer View Site (`server.js`, port 3000)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | Static index.html |
| GET | `/data/products.json` | Product data |
| GET | `/data/categories.json` | Category data |
| GET | `/data/settings.json` | App settings |
| GET | `/data/version.json` | Cache-bust version |
| GET | `/images/*` | Product images |
| GET | `/sw.js` | Service worker |
| GET | `/manifest.json` | PWA manifest |

### CEA Server (`server.py`, port 8081)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/` | none | Customer catalog |
| GET | `/admin` | none | Admin login page |
| GET | `/api/products` | none | Public product list (search, sort, filter) |
| GET | `/api/products/<id>` | none | Single product |
| GET | `/api/settings/public` | none | Shop name, WhatsApp, maps, currency |
| POST | `/api/auth/login` | none | PIN login → token |
| POST | `/api/auth/logout` | token | End session |
| GET | `/api/admin/products` | token | Full product list |
| POST | `/api/admin/products` | token | Create product |
| PUT | `/api/admin/products/<id>` | token | Update product |
| DELETE | `/api/admin/products/<id>` | token | Delete product |
| GET | `/api/admin/settings` | token | All settings |
| PUT | `/api/admin/settings` | token | Update settings |
| GET | `/api/admin/stats` | token | Dashboard stats |
| GET | `/api/admin/backup` | token | JSON backup |
| GET | `/api/admin/export` | token | Export all data |
| POST | `/api/admin/import` | token | Import data |
| POST | `/api/admin/upload` | token | Upload product image |

---

## 5. FRONTEND FEATURES

### Shop Manager UI (`index.html` + `app.js`)
- PIN lock screen with numeric keypad
- Tab navigation: Dashboard | Inventory | Sales | Settings
- Dashboard: stat cards (total items, stock value, today's revenue, items sold today)
- Inventory: card grid with image, name, type, price, qty, stock badge
- Add/Edit modal: name, type, description, price, MRP, purchase cost, location, quantity, camera capture
- Sales: item selector, quantity, price, date, notes
- Search with debounce
- Camera capture (getUserMedia) + file upload
- Responsive mobile-first design
- Drawer navigation
- Change PIN modal

### Customer Site UI (`index.html`)
- Sticky header with brand name + subtitle
- Search bar
- Horizontal scrollable category filter chips
- 2-column product grid
- Product cards: image, category, name, price, MRP (strikethrough), discount badge
- Out-of-stock: orange badge, dimmed card, no cart button
- Featured: green badge
- Product detail modal with full info
- Cart icon with badge
- Cart page: qty controls, total, WhatsApp checkout button
- Guest/auth login modal
- Service Worker registration
- Offline fallback
- Cache-busting on new version

### Customer View Manager UI (`manager.html`)
- Dashboard stats cards
- Product list with visibility toggle, featured toggle, badge editor
- Category manager
- Settings editor (title, subtitle, WhatsApp, theme color, currency, display options)
- Sync button (import + generate)
- Publish button (generate + git push)
- Last sync / last publish timestamps

### CEA Admin UI (`admin.html`)
- PIN login
- Product CRUD table
- Image upload
- Settings editor
- Stats dashboard
- Backup/restore

---

## 6. SYNC & PUBLISH PIPELINE

```
┌─────────────────────────────────────────────────────────────┐
│                    SHOP MANAGER (:8080)                      │
│  Owner adds/edits products → shop.db updated                │
│  Every write triggers auto_backup.py → Google Drive         │
└──────────────────────────┬──────────────────────────────────┘
                           │
                     import.py reads
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│               CUSTOMER VIEW DB (customer-view.db)            │
│  import.py: copies items → products, extracts images,       │
│  calculates discount %, sets stock_status, logs sync        │
└──────────────────────────┬──────────────────────────────────┘
                           │
                     generate.py reads
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                 STATIC SITE (site/)                          │
│  products.json, categories.json, settings.json, images/     │
│  Served on :3000 OR published to GitHub Pages               │
└──────────────────────────┬──────────────────────────────────┘
                           │
               Publish button (git push)
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│            GITHUB PAGES (gh-pages branch)                    │
│  Free HTTPS hosting, auto-deploys on push                   │
│  URL: https://singhvishalvikram.github.io/Cinema123BW      │
└─────────────────────────────────────────────────────────────┘
```

---

## 7. WHAT NEEDS WORK FOR MULTI-SALE

### Current Limitations (Single-Shop Only)

1. **Hardcoded paths** — All scripts use `/root/shop-manager/...` absolute paths
2. **Single database** — One `shop.db`, one `customer-view.db` — no tenant isolation
3. **Single Google Drive folder** — All backups go to one Drive folder
4. **Single GitHub repo for Pages** — Publishing pushes to one repo
5. **No multi-tenancy** — No concept of "shops" or "accounts"
6. **Settings are global** — One `settings` table, no per-shop config
7. **No onboarding flow** — Manual setup required for each new shop
8. **No billing/subscription** — No payment integration
9. **No admin panel for you** — No way to manage multiple shops from one place
10. **Android not native** — PWA only, no Play Store presence
11. **No custom domain support** — Stuck with github.io subdomain
12. **No shop-specific branding** — App title hardcoded as "ARV ENTERPRISES"

### What "Ready for Sale" Means

- [ ] Each shop gets isolated DB + storage
- [ ] Each shop gets own GitHub Pages URL (or custom domain)
- [ ] Shop owner signs up → gets their own manager + catalog instantly
- [ ] Zero manual setup per shop (self-serve)
- [ ] Android APK / Play Store listing
- [ ] White-label (no "ARV" branding visible to end customers)
- [ ] Subscription billing (monthly/annual)
- [ ] You (admin) can see all shops, manage from one dashboard

---

## 8. MULTI-SHOP SELL STRATEGY (BRAINSTORM)

### Model: SaaS for Small Indian Shops

**Target customer:** Small shop owners (electrical, grocery, hardware, general store) who want a digital catalog + inventory management.

**Pricing idea:**
- Free tier: 50 products, basic catalog, GitHub Pages hosting
- Pro tier (₹299/month): Unlimited products, custom domain, Android app, WhatsApp priority
- Enterprise (₹999/month): Multi-location, staff accounts, advanced analytics

### Strategy A: "Instant Shop in 5 Minutes" (Recommended)

1. Shop owner visits `arvshop.in` (your landing page)
2. Signs up with phone number (OTP via WhatsApp/SMS)
3. System auto-provisions:
   - New isolated SQLite DB (or schema prefix)
   - New GitHub repo (or subdirectory) for their Pages site
   - Default settings with their shop name
   - Manager URL: `https://arvshop.in/app/<shop-id>`
   - Customer catalog: `https://<shop-name>.arvshop.in` or custom domain
4. Owner adds products via Manager UI (same as now)
5. One-click publish → live catalog instantly
6. Share catalog link with customers via WhatsApp

**Tech approach:**
- Single Flask app with multi-tenant routing (subdomain or path-based)
- Each shop = separate SQLite file: `shops/<shop_id>/shop.db`
- Each shop = separate GitHub repo for Pages (or use path-based: `arvshop.github.io/catalogs/<shop-id>/`)
- Use GitHub API to auto-create repos on signup
- Settings stored per-shop in their DB

### Strategy B: "White-Label Android App" (Play Store)

1. Build ONE Android app (TWA/Capacitor wrapper around the PWA)
2. App loads shop-specific config from URL parameter or subdomain
3. Each shop owner gets their own APK (or one APK with shop selector)
4. Sell the APK as a one-time purchase (₹1999) or subscription

**Tech approach:**
- TWA (Trusted Web Activity) — wraps the PWA in a native Android shell
- Digital Asset Links → ties the app to the web domain
- One APK works for all shops (loads config from URL)
- Or: auto-generate per-shop APK using CI/CD (GitHub Actions + Bubblewrap)

### Strategy C: "Franchise Model" (You Operate, They Pay)

1. You host everything on your server
2. Each shop gets a subdomain: `electrical-shop.arvshop.in`
3. You manage the infrastructure, they just add products
4. Monthly subscription, you handle all tech
5. Lowest friction for the shop owner

### Strategy D: "WhatsApp-First" (Leverage Existing Behavior)

1. Customer visits catalog → taps WhatsApp button → pre-filled message to shop owner
2. Shop owner gets order notification on WhatsApp
3. Inventory auto-updates on sale entry
4. This is ALREADY WORKING in the current app — just need to make it multi-tenant

### Revenue Streams

| Stream | Type | Amount |
|--------|------|--------|
| Monthly subscription | Recurring | ₹299-999/mo |
| One-time setup fee | One-time | ₹500 |
| Custom domain setup | One-time | ₹200 |
| Android app (per shop) | One-time or sub | ₹1999 or ₹99/mo |
| White-label removal | Add-on | ₹100/mo |
| Priority support | Add-on | ₹200/mo |

### Competitive Advantages

1. **Zero coding required** for shop owner
2. **WhatsApp-native** — meets customers where they already are
3. **Works offline** (Service Worker)
4. **Free hosting** (GitHub Pages)
5. **Google Drive backup** — data safety
6. **Android-ready** — installable PWA or Play Store APK
7. **Indian market** — ₹ pricing, INR currency, WhatsApp integration
8. **Tiny shops** — not competing with Shopify, targeting the unorganized sector

---

## 9. ANDROID DEPLOYMENT ARCHITECTURE

### Option 1: PWA (Progressive Web App) — Easiest, Already Working

**Status:** The customer site already has a `manifest.json` + Service Worker.
- Users can "Add to Home Screen" from Chrome
- Works offline
- No Play Store needed
- **Limitation:** No Play Store discovery, limited push notifications

### Option 2: TWA (Trusted Web Activity) — Play Store Ready

**How it works:**
1. Wrap the PWA in a native Android shell using Bubblewrap or Android Studio
2. The app is essentially a full-screen WebView that loads your PWA URL
3. Digital Asset Links verify domain ownership
4. Upload APK to Play Store

**Steps:**
```bash
# Install Bubblewrap
npm init -y
npm install @bubblewrap/cli

# Initialize TWA project
bubblewrap init --manifest https://your-domain.com/manifest.json

# Build APK
bubblewrap build

# Upload to Play Store
```

**Pros:**
- One APK for all shops (loads config from URL)
- Play Store presence
- Push notifications via FCM
- Auto-updates when PWA updates

**Cons:**
- Need to manage signing keys
- Play Store one-time $25 fee
- Review process

### Option 3: Capacitor (Ionic) — Full Native Control

**How it works:**
1. Take the existing web app
2. Wrap with Capacitor (`npx cap add android`)
3. Add native plugins (camera, push, etc.)
4. Build APK in Android Studio

**Pros:**
- Full native API access
- Can add native features (barcode scanner, etc.)
- Still mostly web code

**Cons:**
- Heavier than TWA
- Need Android SDK (x86-64 only on this server — ARM64 APK blocked)
- More complex build pipeline

### Option 4: Per-Shop APK Generation (CI/CD)

**How it works:**
1. Shop owner signs up → gets their PWA URL
2. GitHub Actions workflow triggers:
   - Reads shop config (branding, URL, colors)
   - Generates TWA with shop-specific assets
   - Signs and uploads APK to your server
3. Owner downloads their branded APK
4. Or: you publish to Play Store under your account

**Architecture:**
```
Shop Signup → GitHub Actions triggered
    ↓
Bubblewrap reads shop's manifest.json
    ↓
Generates APK with shop's icon, name, URL
    ↓
Signs with your keystore
    ↓
Uploads to your server / Play Store
    ↓
Shop owner downloads branded APK
```

### Recommended: Option 2 (TWA) + Option 4 (CI/CD)

- One APK shell that loads any shop's PWA
- OR per-shop APK generated on demand
- Shop owner doesn't need to do anything technical
- You control the Play Store listing

---

## 10. ACTION PLAN — MAKE IT SALE-READY

### Phase 1: Multi-Tenant Foundation (Week 1-2)

- [ ] Refactor all hardcoded paths to use config-based paths
- [ ] Add `shops` table with: id, name, slug, phone, plan, created_at, status
- [ ] Each shop gets: `data/shops/<shop-id>/shop.db`, `data/shops/<shop-id>/customer-view.db`
- [ ] Add middleware to identify shop from subdomain or path
- [ ] Settings table becomes per-shop (already is, since each shop has own DB)
- [ ] Create signup endpoint: `POST /api/signup` → creates shop, DBs, default settings
- [ ] Create login endpoint: `POST /api/login` → returns session token scoped to shop

### Phase 2: Self-Serve Onboarding (Week 2-3)

- [ ] Landing page at `/` — "Create your shop in 5 minutes"
- [ ] Signup form: shop name, owner phone, category, optional logo
- [ ] Auto-provision: create DBs, default settings, GitHub repo for Pages
- [ ] Auto-login after signup → redirect to Manager UI
- [ ] Manager UI pre-filled with shop name, default settings
- [ ] One-click publish → creates GitHub repo, pushes first version

### Phase 3: White-Label & Branding (Week 3-4)

- [ ] Remove all "ARV" references from customer-facing code
- [ ] All branding comes from settings: app_title, logo, theme_color, favicon
- [ ] Customer site reads shop name from settings (already works)
- [ ] Manager UI shows shop owner's branding, not yours
- [ ] Custom favicon per shop (upload in settings)
- [ ] Custom CSS override field in settings

### Phase 4: Android Build Pipeline (Week 4-5)

- [ ] Set up Bubblewrap in CI/CD
- [ ] Create TWA template with configurable:
   - App name (from shop settings)
   - Start URL (shop's catalog URL)
   - Icon (from shop settings)
   - Theme color
   - Package name: `com.arvshop.<shop-slug>`
- [ ] GitHub Actions workflow: on shop signup → build APK → store artifact
- [ ] APK download link in Manager UI
- [ ] Signing keystore management

### Phase 5: Billing & Subscriptions (Week 5-6)

- [ ] Integrate Razorpay (Indian payment gateway)
- [ ] Plans table: free, pro, enterprise with limits
- [ ] Subscription tracking: shop_id, plan, expires_at, status
- [ ] Feature gating based on plan:
   - Free: 50 products, github.io domain, ARV branding
   - Pro: unlimited, custom domain, no branding, Android app
   - Enterprise: multi-location, staff accounts, API access
- [ ] Payment webhook → activate/extend subscription
- [ ] Grace period on expiry → notify owner → downgrade to free

### Phase 6: Admin Dashboard (Week 6-7)

- [ ] Super-admin login (your master PIN)
- [ ] View all shops, plans, revenue
- [ ] Suspend/activate shops
- [ ] Broadcast announcement to all shop owners
- [ ] Analytics: total shops, active shops, MRR, churn

### Phase 7: Polish & Launch (Week 7-8)

- [ ] Custom domain setup guide (CNAME → github.io)
- [ ] WhatsApp OTP for signup (use WhatsApp Business API or manual)
- [ ] Email notifications (optional)
- [ ] Help docs / video tutorials in Hindi
- [ ] Landing page with demo shop, testimonials, pricing
- [ ] Play Store listing (screenshots, description, privacy policy)
- [ ] Terms of service + privacy policy pages

---

## QUICK-WIN REFACTORING CHECKLIST

These are the minimal changes to make the current code multi-tenant ready:

1. **Config file** — `config.json` with base path, not hardcoded `/root/...`
2. **Shop context** — middleware reads `shop_id` from subdomain/path/header
3. **DB path** — `get_db(shop_id)` → `data/shops/<shop_id>/shop.db`
4. **Settings** — already per-DB, just ensure defaults are shop-appropriate
5. **Branding** — replace all hardcoded "ARV ENTERPRISES" with `settings.app_title`
6. **GitHub repo** — parameterize repo path in publish script
7. **Google Drive** — per-shop backup folder (or subfolder)
8. **PIN** — per-shop PIN (stored in settings, not localStorage only)

---

## FILES TO MODIFY FOR MULTI-TENANCY

| File | Change |
|------|--------|
| `shop-manager/backend/app.py` | Config-based DB path, shop context middleware |
| `customer-view/scripts/import.py` | Accept shop_id param, use shop-specific paths |
| `customer-view/scripts/generate.py` | Accept shop_id param |
| `customer-view/manager/server.js` | Shop context, per-shop DB path |
| `customer-view/site/server.js` | Shop context (or generate per-shop static sites) |
| `shop-manager/scripts/auto_backup.py` | Per-shop Drive folder |
| `shop-manager/backend/static/js/app.js` | Read shop name from API, not hardcoded |
| `customer-view/site/index.html` | Dynamic title from settings.json |
| `customer-view/manager/manager.html` | Shop-aware API calls |

---

## SUMMARY

The ARV Shop Manager is a **fully functional single-shop system** with:
- 30+ working API endpoints
- Complete inventory + sales management
- Customer-facing catalog with auth + cart
- Google Drive backup
- GitHub Pages publishing
- PWA with offline support
- Service Worker caching

To make it **multi-shop sellable**, the core work is:
1. **Multi-tenancy** (isolated DBs per shop) — ~1 week
2. **Self-serve signup** (auto-provision everything) — ~1 week
3. **White-label** (remove hardcoded branding) — ~3 days
4. **Android APK** (TWA wrapper + CI/CD) — ~1 week
5. **Billing** (Razorpay integration) — ~1 week
6. **Admin panel** (manage all shops) — ~1 week

**Total estimate: 4-6 weeks to launch-ready SaaS.**

The existing code is solid — it's a refactoring and packaging job, not a rebuild.
