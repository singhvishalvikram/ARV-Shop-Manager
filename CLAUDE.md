# CLAUDE.md — ARV Shop Manager

> **This file is binding. Read it before doing anything in this repo.**

## 0. Single Source of Truth (NON-NEGOTIABLE)

The governing engineering ruleset for this repository is the **Enterprise Coding
Standards**, located at:

```
/Users/vikranty/Documents/Project/OLD Lap Work/Roadmap/Enterprise-Coding-Standards/
├── CODING_STANDARDS.md   # Pillars 0–7: naming, architecture, FE, BE/API, docs, git, testing
├── GUARDRAILS.md         # Security: STRIDE, OWASP+, edge cases, DevSecOps, prod safeguards
├── pipeline_ops.md       # CI verify, security scanning, deploy/rollback, monitoring, FinOps
└── prompt_design.md      # Planning pipeline: Discovery → PRD → RFC → Roadmap → TDD
```

Rules:
- **Always follow these. Never break them. Never take a shortcut.**
- On any conflict between this file, `agent.md`, the audit doc, and the Standards —
  **the Standards win.** (Precedence: Enterprise Coding Standards > CLAUDE.md > agent.md > audit.)
- **Always design for scalability**, even when the immediate task is small. No solution
  that only works for a single shop / single machine is acceptable for new code.
- This is **Commercial Tier** (the product will be sold; it handles user accounts and,
  later, payments). Commercial Tier mandates — and these are not optional here:
  - Universal Response Envelope (CODING §4.2)
  - API Versioning, `/api/v1/...` (CODING §4.6)
  - CSRF protection on mutating routes (CODING §4.1.1)
  - Central Error Code Registry (CODING §2.5.1)
  - Structured logging + observability (CODING §4.7)
  - Parameterized queries / injection prevention (GUARDRAILS §2.2)
  - Secure session & auth, modern password hashing (GUARDRAILS §2.5)
  - Zero-downtime migration safety, expand/contract (GUARDRAILS §6.1)

## 1. What this project is

Multi-shop SaaS (target: small Indian retail) — inventory + sales manager for the owner,
plus a WhatsApp-checkout product catalog for customers. Tenancy unit = **the shop**.
See `agent.md` (codebase guide) and `APP-AUDIT-AND-SELL-STRATEGY.md` (strategy) for context,
but treat the audit's claims as **unverified** — verify against code before relying on them.

## 2. Verified current state (read the CODE, not the audit)

The audit oversells readiness. Confirmed from source this session:

| Reality | Evidence |
|---|---|
| Flask API has **NO authentication** — the "PIN" is client-side `localStorage` only | `shop-manager/backend/app.py` (no auth on any `/api/*` route) |
| Manager runs SQL by writing a Python file to `/tmp` and shelling out **per query**, with hand-rolled quote escaping (injection + process bomb) | `customer-view/manager/server.js:21-67` |
| Passwords = **unsalted SHA-256 + secret hardcoded in source** | `customer-view/manager/server.js:12` |
| Hardcoded `/root/...` paths and a single GitHub repo in the publish path | `app.py:28-30`, `server.js:343-372` |
| Latent bug: `json.load` used but `json` never imported (hidden by bare `except`) | `app.py:36` |
| `.bak` / `.backup` files committed; no tests; no CI | repo tree |

## 3. Data model (verified)

- **`shop.db`** (source of truth): `items` (id, name, type, description, price, mrp,
  purchase_cost, image_url, location, quantity, created_at, updated_at) and
  `daily_sales` (id, item_id→items.id, quantity_sold, sale_price, sale_date, description).
  `stock_status` is **computed**, not stored (`quantity <= 0 → out_of_stock`).
- **`customer-view.db`** (derived via `import.py`): `products` (mirrors items + merch
  fields: visible, featured, badge, sort_order, *_override, discount_percent, stock_status),
  `categories`, `settings` (key/value), `sync_log`, `users`, `user_cart`, `sessions`.
- The "CEA / `cea.db`" in the audit is **not present** in this repo — stale; ignore.

## 4. Direction (decided via Council review)

- **Do NOT** do the audit's "4–6 week multi-tenant rebuild" until demand is validated
  with at least one real paying shop.
- When multi-tenancy is built, the target architecture is **one service + one Postgres DB +
  `shop_id` foreign key + Row-Level Security** — **NOT** file-per-shop SQLite and **NOT**
  repo-per-shop (both fail to scale). Move images to object storage; drop base64-in-DB.
- The two security fixes below are **mandatory before any second party's data** touches the
  system, regardless of the build/no-build decision:
  1. Real server-side authentication + authorization on every API route.
  2. Replace SHA-256 password hashing with **argon2id**; remove the hardcoded secret.

## 5. Before changing code

1. Identify the Standards' tier rules that apply (Commercial Tier — see §0).
2. Follow the planning pipeline in `prompt_design.md` for anything non-trivial
   (PRD → RFC → Roadmap → TDD) before writing code.
3. No new hardcoded host paths, secrets, or single-tenant assumptions.
4. Parameterized queries only. No string-built SQL. No subprocess-per-query.
5. Write tests (none exist today — establish the harness first; see GUARDRAILS Module 4).
