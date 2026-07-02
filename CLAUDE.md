# CLAUDE.md — ARV Shop Manager

> **Binding. Read before touching any file in this repo.**
> Companion docs: `AGENTS.md` (canonical codebase guide — structure, commands, guardrails)
> and `APP-AUDIT-AND-SELL-STRATEGY.md` (feature audit + SaaS strategy; treat claims as
> unverified until checked against code).

## 0. Governing Standards (NON-NEGOTIABLE)

This repo is governed by the Enterprise Standards at `~/Documents/Prompts/`
(see `~/Documents/CLAUDE.md` for the full index and loading rules).

- **Precedence:** `GUARDRAILS.md` > `pipeline_ops.md` > `CODING_STANDARDS.md` >
  Tier-2 domain files > this file > `AGENTS.md` > audit doc.
- **Risk tier: Commercial/Production.** This product will be sold to shopkeepers and
  handles customer auth data (phone numbers, password hashes) and, later, payments.
  All GUARDRAILS modules and all CODING_STANDARDS pillars apply.
- **Never take shortcuts. Always design for scalability** — the roadmap is multi-tenant
  SaaS + Android app; no new code may assume a single shop, a single machine, or `/root/`.

## 1. What This Is

A shop-management platform for small Indian retail shops with two personas:

- **Shop owner (admin):** Flask app (`shop-manager/backend/app.py`, port 8080) —
  inventory CRUD with camera/photo upload, sales recording, dashboard, Google Drive
  auto-backup on every write. PIN-locked (client-side only — known gap).
- **Customer (storefront):** PWA catalog served locally (port 3000) or on GitHub Pages —
  browse, search, cart, WhatsApp checkout. Curated via the View Manager (Node, port 3001):
  toggle visibility/featured/badges, then one-click Sync + Publish (`git push` to
  `gh-pages` of the catalog repo).

**Data flow:** `shop.db` → `import.py` → `customer-view.db` → `generate.py` →
`site/data/*.json` → publish → GitHub Pages CDN → customer PWA (service worker,
`version.json` cache-bust).

**Client's end goal (from stakeholder transcript):** shopkeepers *without computers* must
run everything from **apps on a phone** — (1) an inventory-management app and (2) a
storefront-control app that manages what customers see (products, shop name, location)
and publishes the shareable website via GitHub or a custom domain. The current Debian
desktop flow is the prototype; every change should move toward this app-based,
self-serve future (TWA/Bubblewrap plan in `AGENTS.md` → "Mobile App Extension Plan").

## 2. Hard Rules for This Repo

### Never

- Commit `*.db`, `backups/`, product images, `.env`, or weaken `.gitignore`.
- Hand-edit generated files (`site/data/*.json`, `git-pages/data/*.json`) — regenerate
  via the pipeline.
- Add npm dependencies — Node servers intentionally use only built-ins. Flask deps stay
  minimal (`flask`, `Pillow`). Human approval required to change this.
- Hardcode absolute paths (`/root/...`) or secrets — existing instances are **debt to
  remove**, not patterns to copy.
- `git push` or change DB schema without explicit human approval + migration script.
- Edit `.bak`/`.backup` files.

### Always

- Bump `CACHE_NAME` in the relevant `sw.js` whenever cached assets change.
- Call `mark_write()` on any new Flask endpoint that mutates data (triggers backup).
- Keep sensitive fields (`purchase_cost`, `location`, raw `quantity`) out of anything
  customer-facing — verify `import.py`/`generate.py` output after touching them.
- Run the manual test checklist in `AGENTS.md` → "Testing Strategy" after changes to
  `app.py`, `import.py`, `generate.py`, or `manager/server.js`.
- Use `__file__`-relative paths, parameterized SQL, `sqlite3.Row`, WAL mode, explicit
  connection close (Python); ES6+, `parseBody()`/`sendJson()` server pattern (Node).

## 3. Verified Current State (read code, not just docs)

Confirmed weaknesses — fix opportunistically, never replicate:

| Issue | Where |
|---|---|
| No server-side auth on any `/api/*` route (PIN is `localStorage` only) | `shop-manager/backend/app.py` |
| View Manager `/api/*` unauthenticated; localhost-only assumption | `customer-view/manager/server.js` |
| Node runs SQL by shelling out to Python with hand-rolled escaping | `customer-view/manager/server.js` |
| Passwords: SHA-256 + hardcoded `AUTH_SECRET` salt in source | `customer-view/manager/server.js` |
| Hardcoded `/root/...` paths in backup/publish paths | `app.py`, `auto_backup.py`, `generate.py`, `manager/server.js` |
| Latent bug: `json.load` used but `json` never imported (masked by bare `except`) | `app.py` (Drive-config block, ~line 33) |
| `git-pages/manifest.json` has `"icons": []` — blocks PWA install & TWA build | `git-pages/manifest.json` |
| Backup depends on external `hermes/google_api.py` not in this repo | `auto_backup.py` |
| No tests, no CI (`.github/workflows/` absent), no pagination on `/api/items` | repo-wide |

Any new feature that touches these areas must fix the underlying gap per the Standards
(e.g., new mutating endpoint → server-side auth + validation, not another open route).

## 4. Roadmap Priorities (ordered — see `AGENTS.md` for detail)

1. **Stabilize:** relative paths, real PWA icons, `AUTH_SECRET` → env, server-side PIN
   validation, pytest suite for Flask API, document the hermes dependency.
2. **Multi-tenant foundation:** `shops` table, per-shop DB scoping, parameterized
   `import.py`/`generate.py`/publish.
3. **Self-serve onboarding** (signup → auto-provisioned shop + repo, white-label via
   `settings`).
4. **Android apps** — native Java customer app implemented at `mobile/customer-app/`
   (see `MOBILE-ARCHITECTURE.md`: 20-phase plan, build commands, admin-app
   prerequisites). Admin app is blocked on server-side auth (item 1).
5. **Billing (Razorpay) → super-admin dashboard.**

## 5. Workflow for Any Task Here

1. Read this file + relevant `AGENTS.md` sections.
2. State risk tier (Commercial) and which standards files apply; load Tier-2 docs from
   `~/Documents/Prompts/` for DB/API/frontend/testing work.
3. For complex features, run the planning pipeline (`Prompts/prompt_design.md`):
   Discovery → PRD → RFC → task roadmap → tests-first.
4. Implement per standards; verify with the manual checklist; report results honestly.
5. Update `AGENTS.md` whenever architecture, API contracts, or deployment flow change.
