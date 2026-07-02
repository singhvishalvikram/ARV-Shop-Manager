# ARV Shop Manager

Shop management system: inventory + sales for the owner, and a public
WhatsApp-checkout product catalog for customers.

> **Architecture is consolidating into a single app.** The owner manager
> (Flask) and the customer view (Node) are being merged into **one FastAPI
> service backed by one SQLite database**. The legacy multi-server / two-DB
> pipeline (`import.py` → `customer-view.db` → `generate.py`) is being retired.
> See [`CLAUDE.md`](CLAUDE.md) for the governing rules and current state.

## Target architecture (one brain, two faces)

```
                  ┌──────────────────────────────────────┐
                  │   FastAPI service  (shop-manager/      │
                  │   backend/app, /api/v1)                │
                  │                                        │
   Owner UI  ───► │  auth · items · sales · dashboard ·    │ ◄─── argon2id auth,
   (admin)        │  settings   (owner-only, authed)       │      sessions
                  │                                        │
   Customer  ───► │  catalog · cart   (public / customer)  │ ◄─── safe fields only
   catalog UI     │                                        │      (no cost price)
                  └───────────────────┬────────────────────┘
                                      │
                              one SQLite DB (shop.db)
                     items · daily_sales · users · sessions ·
                          settings · user_cart
```

Two front-end surfaces remain separate **by design**: the owner admin UI and
the public catalog have opposite trust boundaries. The catalog must never see
`purchase_cost`, `location`, or raw `quantity` — enforced by the `/catalog`
routes and covered by tests.

## Quick start (consolidated backend)

```bash
cd shop-manager/backend
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt -r requirements-dev.txt

# Run the API
uvicorn app.main:app --host 0.0.0.0 --port 8080
# OpenAPI docs: http://localhost:8080/docs

# Run the tests
python -m pytest -q
```

Configuration is environment-driven — copy [`.env.example`](.env.example) to
`.env`. No hardcoded host paths.

## API surface (`/api/v1`)

| Area | Routes | Auth |
|------|--------|------|
| Auth | `POST /auth/signup`, `/auth/login`, `/auth/logout`, `GET /auth/me` | public / token |
| Items | `GET/POST /items`, `GET/PUT/DELETE /items/{id}` | owner |
| Sales | `POST /sales`, `GET /sales` | owner |
| Dashboard | `GET /dashboard` | owner |
| Settings | `GET/POST /settings` | owner |
| Catalog | `GET /catalog/products`, `/catalog/categories`, `/catalog/settings` | public |
| Cart | `GET/POST /cart`, `DELETE /cart/{item_id}` | customer |
| Health | `GET /health` | public |

All responses use the standard envelope:
`{"success", "data", "error"}`.

## Database (single DB)

`shop-manager/backend/shop.db` (SQLite, WAL):

- `items` — inventory + merchandising flags (`visible`, `featured`, `badge`,
  `sort_order`, `title_override`, `description_override`)
- `daily_sales` — sales log (decremented atomically on sale)
- `users`, `sessions` — argon2id auth + server-side sessions
- `settings` — key/value app config (white-label ready)
- `user_cart` — customer carts

`stock_status` and `discount_percent` are **computed** (see
`shop-manager/backend/domain.py`), never stored.

## Status of legacy components

| Component | State |
|-----------|-------|
| `shop-manager/backend/app.py` (Flask) | legacy; routes migrated to FastAPI, pending removal |
| `customer-view/` (Node servers, `import.py`, `generate.py`) | being retired; backend folded into FastAPI |
| `git-pages/` | static GitHub Pages catalog; will be generated from the one DB |

## Tech stack

- **Backend**: Python 3.11, FastAPI, Pydantic, argon2-cffi, SQLite (WAL)
- **Frontend**: Vanilla JS PWA (owner admin + customer catalog)
- **Hosting**: GitHub Pages (static catalog), Google Drive (DB backup)
- **Quality**: pytest + ruff, GitHub Actions CI (lint + test + secret scan)

## Engineering standards

This repo follows the **Enterprise Coding Standards** as its single source of
truth (Commercial Tier). See [`CLAUDE.md`](CLAUDE.md) §0.
