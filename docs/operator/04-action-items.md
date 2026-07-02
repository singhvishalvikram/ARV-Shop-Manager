# 4. Action items — what blocks what

The consolidated checklist of **your** tasks, in order. Status as of 2026-06-29.

## Do now (unblocks everything else)

| # | Task | Why it matters | Blocks | Doc |
|---|------|----------------|--------|-----|
| 1 | **Browser-verify** the owner app + catalog locally | I verified the backend (75 tests) but not the DOM/camera/SW | Confidence to deploy | [01](01-run-and-verify.md) |
| 2 | Generate & set **`AUTH_SECRET`** | Required for sessions + OAuth state; empty by design | Login in prod, Google login | [02](02-google-oauth-setup.md) / [03](03-deployment.md) |

## Do before going live

| # | Task | Why it matters | Blocks | Doc |
|---|------|----------------|--------|-----|
| 3 | Provision an **HTTPS host** + reverse proxy (forward `X-Forwarded-For`) | Camera, PWA install, OAuth, TWA all need HTTPS | Real-device use, Google login | [03](03-deployment.md) |
| 4 | Deploy the FastAPI service with a **persistent volume** (DB + images) | Data durability | Production | [03](03-deployment.md) |
| 5 | Create the **Google OAuth client** + set the 4 env vars | Enables "Sign in with Google" | Google login only (password works without it) | [02](02-google-oauth-setup.md) |
| 6 | Decide **catalog hosting** (same-origin `/catalog` vs cross-origin + CORS + `api-base`) | Determines whether the catalog shows live inventory | Live-inventory catalog; catalog Bearer cutover | [03 §3.5](03-deployment.md) |

## Engineering follow-ups (I do these — listed so you can sequence/approve)

| # | Task | Trigger |
|---|------|---------|
| 7 | Catalog **auth + cart Bearer cutover** (off legacy cookie endpoints) | After the API is deployed + reachable (item 4) |
| 8 | **Object storage** for images (swap the `local` backend) | When you outgrow a single instance |
| 9 | **Redis-backed rate limiting** | When you run multiple instances |
| 10 | Owner **front-end test suite** (Vitest/Playwright) — Phase 9 | Needs a JS test runner added to the repo; say the word |
| 11 | **Remove inline JS** so CSP can drop `'unsafe-inline'` | Hardening pass |
| 12 | **DSR tooling** (export/delete a user's data) — DPDP | Before scale / first data request |

## Deferred until demand is validated (per the LLM Council + CLAUDE.md §4)

- Multi-tenant rebuild: **Postgres + `shop_id` + Row-Level Security** (not file/repo per shop).
- **TWA build + Play Store** publishing (Phases 10/15/16); keystore custody in cloud CI.
- **FCM push** notifications; hosted log/error platform (Sentry/Grafana).
- Staged rollout / canary tooling (Phase 19).

## Definition of "ready to onboard the first shop"

Items **1–6** done, CI green, and the owner checklist in [01](01-run-and-verify.md) passes on
a real phone over HTTPS. At that point a shop can run the owner PWA and share its catalog link.
