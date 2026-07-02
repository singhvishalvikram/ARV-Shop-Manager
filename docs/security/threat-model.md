# Threat Model & Data-Handling (ARV Shop Manager)

> Risk tier: **Commercial** (sold product; user accounts; payments later).
> Governing rules: Enterprise Coding Standards + GUARDRAILS. This is a living doc.

## Assets & data inventory

| Data | Where | Sensitivity | Control |
|------|-------|-------------|---------|
| Owner/customer **password** | not stored; only the hash | high | **argon2id** (per-call salt), never logged |
| **phone**, **name**, **email** (PII) | `users` | medium | least-exposure; never sent to the catalog |
| **session tokens** | `sessions` (server-side), client `localStorage` | high | opaque random, server-side expiry (TTL) |
| inventory incl. **purchase_cost** | `items` | business-confidential | owner-only routes; **never** in `/catalog` |
| **cart**, **sales** | `user_cart`, `daily_sales` | low/medium | per-user / owner-only |
| item images | object/disk storage | low | type+size validated, server-named files |

## STRIDE summary & controls

- **Spoofing:** server-side sessions + `require_auth` on every owner route; Google OIDC
  (Authlib) for federated login. Auth endpoints are **rate-limited** (per-IP, 5/15min) to
  blunt brute force.
- **Tampering:** parameterized SQL everywhere (no string-built SQL, no subprocess-per-query);
  Pydantic validation at the edge; transactions at the service layer for stock.
- **Repudiation:** structured logging; `last_login` recorded; (audit trail — future).
- **Information disclosure:** the catalog projection exposes **customer-safe fields only**
  (no `purchase_cost`/`location`/raw `quantity`) — enforced and **tested**. Security headers
  + **CSP** on every response. Secrets via env only (no hardcoded secrets in source).
- **Denial of service:** auth rate-limiting; image size caps; (edge/WAF + per-route limits
  at scale — future, with a shared store).
- **Elevation of privilege:** role on `users`; owner routes gated; OAuth users get an empty
  password hash (cannot password-login).

## CSRF posture

The API authenticates with **Bearer tokens in the `Authorization` header**, not ambient
cookies. Browsers do not attach Authorization headers cross-site, so classic CSRF does not
apply to the owner API; `require_auth` accepts **only** the header (no cookie fallback).
The Google OAuth handshake uses a signed state cookie (Authlib) with the OAuth `state`
parameter. (The **legacy** catalog cookie auth is being retired; its Bearer cutover is
tracked in the mobile roadmap, Phase 11.)

## Headers / transport

- Per-response: `Content-Security-Policy` (default-src 'self'; frame-ancestors 'none'; …),
  `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`,
  `Cache-Control: no-store`.
- **HTTPS is required in production** (camera/getUserMedia and secure cookies need it) —
  enforced at the deployment/edge layer.

## DPDP (India) data-handling notes

- **Minimal PII:** phone, name, optional email. Purpose: account + cart/order continuity.
- **No payment data** is stored today; adding payments requires a fresh review (PCI/DPDP).
- **Retention:** sessions expire (TTL, default 30d). User-data export/delete on request is a
  **known gap to implement** before scale.
- **Processors:** Google (OIDC sign-in), WhatsApp (checkout handoff via link). No customer
  PII is sent to the public catalog.

## Known gaps / future hardening (tracked)

- Remove inline `<script>`/handlers so CSP can drop `'unsafe-inline'` (nonce/external JS).
- Shared-store rate limiting (Redis) for multi-instance scale.
- Object storage for images (replace local disk) — interface already in place.
- DSR (data subject request) export/delete tooling.
- Catalog auth/cart Bearer cutover (Phase 11) + dependency-scan triage cadence.
