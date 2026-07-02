# ADR-004 — Owner auth: FastAPI + Authlib (Google OIDC), not a Node/better-auth rewrite

- **Status:** Accepted
- **Date:** 2026-06-29
- **Deciders:** Project owner + engineering
- **Standards:** GUARDRAILS §2.5 (secure session & auth), §1.4 (secrets), CLAUDE.md §0/§4

## Context

The owner app needs real login, and the owner asked for **Google sign-in** and proposed the
**better-auth** library. better-auth is a TypeScript/JavaScript library — it runs on
Node/Bun/Deno, not on our consolidated **Python/FastAPI** backend. Honoring it literally
would mean either standing up a **separate Node auth service** (re-introducing the
multi-runtime sprawl ADR-002 removed) or **rewriting the entire backend into Node**
(discarding the FastAPI service, argon2id auth, and 40 passing tests we just hardened).

## Decision

Implement owner authentication **natively in the existing FastAPI service**:

- Keep the existing **argon2id phone/password** login + server-side opaque-token sessions.
- Add **Google Sign-In via OIDC** using **Authlib** (the mature, widely-audited Python
  OAuth/OIDC library). Google accounts are provisioned/linked into the same `users` table
  and issued the **same session token** as password users — one session model.
- Google routes are **feature-flagged**: they load only when `GOOGLE_CLIENT_ID`/`SECRET`
  are configured, so the dependency is optional and the test suite needs no network.
- OAuth client credentials live in **env config**, never in source (GUARDRAILS §1.4).

Do **not** rewrite into Node, and do **not** add a separate Node auth service.

## Rationale (safer + more scalable)

- **Auth safety is library/architecture-driven, not runtime-driven.** Authlib and
  better-auth are both secure; safety comes from maturity + tests + small diffs.
- **A full rewrite is the least-safe option** — it discards verified, hardened code and
  re-opens the exact vulnerabilities (no-auth, SHA-256, SQL-injection) we just closed.
- **Scalability is decided by data architecture** (Postgres + `shop_id` + RLS + object
  storage, Phase 14), which is identical regardless of API runtime. FastAPI is async and
  not the bottleneck at retail scale.
- Per CLAUDE.md §4 / the Council, large rebuilds wait for demand validation.

## Consequences

- One backend, one session model; Google is an additional credential, not a parallel stack.
- Requires a Google Cloud OAuth client (ID/secret/redirect URI) provisioned by the owner.
- New nullable `users` columns (`email`, `auth_provider`, `provider_sub`) added via
  idempotent, additive migration (expand/contract, GUARDRAILS §6.1).
- `Authlib` + `itsdangerous` become optional backend dependencies, imported only when
  Google sign-in is configured.

## Alternatives rejected

- **Separate Node + better-auth service:** conflicts with ADR-002 (one backend); added ops.
- **Full Node rewrite:** discards hardened, tested code; re-opens closed vulnerabilities.
