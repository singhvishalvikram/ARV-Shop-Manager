# ADR-002 — One white-label shell per face; no per-shop APKs or repos

- **Status:** Accepted
- **Date:** 2026-06-29
- **Deciders:** Project owner + engineering
- **Standards:** Enterprise Coding Standards (scalability mandate), CLAUDE.md §4

## Context

The legacy approach trended toward per-shop artifacts (a GitHub repo / static build per
shop). With more than a handful of shops this becomes unmaintainable: every shop is a
separate deploy, a separate manifest, a separate APK to rebuild and re-sign. CLAUDE.md
mandates designing for scalability — "no solution that only works for a single shop /
single machine is acceptable for new code."

## Decision

- There is exactly **one shell per face** (one owner app, one catalog app), built once.
- Branding (name, theme color, logo, currency, WhatsApp number) is **configuration loaded
  at runtime from `settings`**, not baked into the build.
- A shop is resolved at runtime — from the authenticated owner session (owner app) or from
  a slug/subdomain in the URL (catalog). No per-shop code, no per-shop APK, no per-shop
  repo.

## Consequences

- A single signed APK serves all owner tenants; a single catalog deployment serves all
  shops, each branded from its own settings.
- The white-label layer (`shared/config/runtime-config.js`) is the only place branding is
  applied; everything else is shop-agnostic.
- Multi-tenant data isolation (backend `shop_id` + RLS) is a separate, **deferred** effort
  (Phase 14), gated on demand per CLAUDE.md §4.

## Alternatives rejected

- **Repo/build/APK per shop:** O(shops) maintenance; fails the scalability mandate.
- **Compile-time branding constants:** forces a rebuild + re-sign + re-publish per shop.
