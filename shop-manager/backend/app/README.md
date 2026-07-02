# Backend service (`app/`)

The single consolidated FastAPI service for ARV Shop Manager. Merges the former
owner manager (Flask) and customer view (Node) into one backend over one SQLite
database. Standards: Enterprise Coding Standards, Commercial Tier (see
[`../../../CLAUDE.md`](../../../CLAUDE.md)).

## Layout

| Path | Responsibility |
|------|----------------|
| `main.py` | App factory, exception handlers (envelope), security headers, router wiring |
| `db.py` | Connection + `init_schema` (parameterized queries only) |
| `schemas.py` | Pydantic request models — the validation source of truth |
| `core/config.py` | Env-driven settings (no hardcoded paths) |
| `core/envelope.py` | Universal Response Envelope `{success, data, error}` |
| `core/errors.py` | Central error-code registry + `AppError` |
| `core/security.py` | argon2id hashing, sessions, `require_auth` dependency |
| `routers/` | One module per resource (see below) |
| `../domain.py` | Pure business rules (`stock_status`, `discount_percent`) |

## Routers (`/api/v1`)

- **Owner (auth required):** `auth`, `items`, `sales`, `dashboard`, `settings`
- **Customer:** `catalog` (public, safe fields only), `cart` (per authenticated user)
- **Ops:** `health`

## Run / test

```bash
# from shop-manager/backend
uvicorn app.main:app --host 0.0.0.0 --port 8080   # OpenAPI: /docs
python -m pytest -q
ruff check app domain.py tests
```

## Invariants (do not break)

1. **Parameterized SQL only** — never string-build queries; never shell out per query.
2. **Catalog exposes customer-safe fields only** — no `purchase_cost`, `location`, or raw
   `quantity`. Widen the `/catalog` projection deliberately, never by `SELECT *`.
3. **Every owner route requires a valid session** (`require_auth`).
4. **Every response is an envelope**; every error has a registry code.
5. **`domain.py` stays pure** (no I/O, no framework) so rules are shared and unit-testable.
