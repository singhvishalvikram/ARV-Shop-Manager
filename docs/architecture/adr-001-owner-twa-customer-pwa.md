# ADR-001 — Owner app ships as a TWA; customer catalog stays a PWA link

- **Status:** Accepted
- **Date:** 2026-06-29
- **Deciders:** Project owner + engineering
- **Standards:** Enterprise Coding Standards §5.4 (ADRs), Commercial Tier

## Context

We have one FastAPI backend (`/api/v1`) and two front-end faces: an **owner** admin
(inventory + sales) and a **customer** catalog (browse + WhatsApp checkout). There is no
native mobile app today; instead there are three fragmented PWAs with broken manifests.
We must decide how each face is delivered to phones.

Constraints:
- Owners want an installable, app-like experience (home-screen icon, full screen, offline).
- Customers must reach the catalog with **zero install friction** — they arrive from a
  WhatsApp link.
- The two faces have opposite trust boundaries; the catalog must never expose
  `purchase_cost` / `location` / raw `quantity`.

## Decision

1. The **owner** face is delivered as a **Trusted Web Activity (TWA)** — a thin Android
   shell wrapping the hosted owner PWA, distributable as a single APK/AAB.
2. The **customer** catalog is delivered as an **installable PWA reached via a link**
   (WhatsApp / "Add to Home Screen"). It is **not** published to an app store.
3. Owner and customer are **never** fused into one downloadable app (ADR-002 covers
   white-labeling; this ADR covers the distribution split).

## Consequences

- Owners get a real store-installable app without a separate native codebase; the web app
  remains the single source of truth.
- Customers face no install/update barrier; the catalog updates instantly on the web.
- We maintain two manifests / two start URLs but one backend and one shared front-end core.
- TWA requires Digital Asset Links + a verified HTTPS origin (see Phase 10).

## Alternatives rejected

- **Native (Flutter/React Native):** second codebase, no demand justification yet.
- **Publishing the catalog to the Play Store:** install friction kills WhatsApp-driven
  customer reach; review overhead with no upside.
- **One app for both faces:** violates the trust boundary and the distribution needs.
