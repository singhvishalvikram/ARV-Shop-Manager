# ARV Shop Manager — Mobile & Storefront Suite

This directory holds the full customer-and-owner front-end suite for the consolidated
V01 platform. Everything talks to (or is served by) the one FastAPI backend at
`../shop-manager/backend` (`/api/v1`).

## What's here

| App / surface | Path | Who | Talks to |
|---|---|---|---|
| **Backend** (FastAPI, `/api/v1`) | [`../shop-manager/backend/`](../shop-manager/backend/) | — | SQLite; argon2id auth; serves the PWA + live catalog |
| **Admin app** (Android, Java) | [`admin-app/`](admin-app/) | Shop owner | the backend (authenticated: items, sales, dashboard, settings) |
| **Customer app** (Android, Java) | [`customer-app/`](customer-app/) | Customer | Static catalog JSON (GitHub Pages) + WhatsApp — **no backend needed** |
| **Customer PWA** | [`../git-pages/`](../git-pages/) | Customer (web) | Served live by the backend (`StaticFiles`) / GitHub Pages |

**The backend is required for the admin/owner experience** (accounts, inventory, sales,
dashboard). It lives in this same repo at `../shop-manager/backend/` and is deployed once
behind HTTPS — see [`DEPLOYMENT.md`](DEPLOYMENT.md). The customer app is standalone and
needs no backend. The backend also serves the PWA and a live catalog API
(`/api/v1/catalog/*`), so the customer app can later point at the live API instead of
static JSON with no redesign.

## Documentation

| Doc | What it covers |
|---|---|
| [`DEVICE-INSTALL.md`](DEVICE-INSTALL.md) | **Will the APK run on my phone?** Android versions, sideloading, why the admin app needs a backend URL |
| [`DEPLOYMENT.md`](DEPLOYMENT.md) | **Deploy the backend + build/ship APKs** that run on any phone; release signing & Play path |
| [`NEXT-STEPS.md`](NEXT-STEPS.md) | Short, prioritized "what to do next" sequence (the roadmap distilled) |
| [`ADMIN-APP-BLUEPRINT.md`](ADMIN-APP-BLUEPRINT.md) | Admin app: Discovery→PRD→RFC→20-phase plan, verified API contract |
| [`MIGRATION-VERIFICATION.md`](MIGRATION-VERIFICATION.md) | Web→mobile parity matrix + live test evidence (owner + customer) |
| [`PRODUCT-ROADMAP.md`](PRODUCT-ROADMAP.md) | Path to sellable: multi-tenancy, billing, features, **security roadmap** |
| [`admin-app/README.md`](admin-app/README.md) | Build/run/test the admin app; demo credentials |
| [`customer-app/README.md`](customer-app/README.md) | Build/run the customer app |
| [`customer-app/FUNCTIONALITY-AND-PARITY.md`](customer-app/FUNCTIONALITY-AND-PARITY.md) | Customer web→app parity + gaps |

## Quick start (owner flow)

```bash
# 1. Backend (venv: fastapi uvicorn argon2-cffi pydantic pillow)
cd ../shop-manager/backend && export AUTH_SECRET=devsecret_local
uvicorn app.main:app --host 0.0.0.0 --port 8000

# 2. Admin app on an emulator
cd admin-app && gradle wrapper --gradle-version 8.7
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
./gradlew installDevDebug
# Log in: 9999999999 / owner1234  (seeded), or create a shop account in-app
```

## Standards

Both apps are Commercial-tier and follow `GUARDRAILS.md` / `CODING_STANDARDS.md` /
`pipeline_ops.md`: native Java + MVVM, AndroidX/Material only (no heavy third-party
deps), server-authoritative validation, encrypted token storage (admin), HTTPS-only in
prod. Tests: backend 75 pytest; admin 12 unit + 4 Espresso; customer 25 unit.
