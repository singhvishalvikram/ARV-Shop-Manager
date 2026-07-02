# Shared front-end core (`static/shared/`)

Framework-free ES modules shared by both front-end faces (owner admin + customer
catalog). Standards: Enterprise Coding Standards, Commercial Tier.

## Clean-architecture rule

UI → state → **service layer (`api/`)** → `http-client` → `/api/v1`.
**No component may call `fetch` directly** (Pillar §3.3). Add an API call by adding a
function to the relevant `*-service.js`, never by calling `fetch` from a page.

## Layout

| Path | Responsibility |
|------|----------------|
| `api/env.js` | Resolve API base URL (window global → `<meta>` → same-origin `/api/v1`). Public config only, never secrets. |
| `api/http-client.js` | The only `fetch` wrapper: bearer token, envelope unwrap, `ApiError` (registry code), `AbortSignal`. |
| `api/auth-service.js` | signup / login / logout / me; stores the session token. |
| `api/items-service.js` | owner inventory CRUD. |
| `api/sales-service.js` | owner sales create / list. |
| `api/dashboard-service.js` | owner dashboard stats. |
| `api/catalog-service.js` | **public** products / categories / settings (safe fields only). |
| `api/cart-service.js` | per-authenticated-user cart. |
| `api/settings-service.js` | owner full settings get / update. |

## Invariants

1. Every service returns the **unwrapped `data`** payload; failures throw `ApiError`.
2. `catalog-service` is `auth:false` and renders **only** customer-safe fields — never
   `purchase_cost`, `location`, or raw `quantity`.
3. Branding is applied only via `config/runtime-config.js` (Phase 3), not hardcoded.
