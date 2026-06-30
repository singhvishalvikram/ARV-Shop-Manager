# ADR-003 — Android artifacts build in cloud CI, never on the host box

- **Status:** Accepted
- **Date:** 2026-06-29
- **Deciders:** Project owner + engineering
- **Standards:** pipeline_ops Module 3 (build-once/deploy-many), GUARDRAILS (secrets, supply chain)

## Context

The production host is x86/ARM-constrained and is a runtime server, not a build machine.
Building and **signing** Android artifacts on it would (a) couple builds to a fragile box,
(b) put the signing keystore on a production server, and (c) make builds non-reproducible.

## Decision

- TWA APK/AAB artifacts are built in **GitHub Actions** (cloud CI), using Bubblewrap.
- The **signing keystore** lives only in CI secrets (and an offline backup under documented
  custody); it never touches the application server or the repo.
- Releases are produced from a tagged commit — **build once, promote the same artifact**
  through testing → production tracks.

## Consequences

- Reproducible, auditable builds decoupled from the runtime host.
- Keystore custody/rotation must be documented before the first signed build (Phase 15);
  **keystore loss is unrecoverable** — it is a release-blocking risk to manage early.
- Local debug builds are still allowed for development (unsigned / debug keystore only).

## Alternatives rejected

- **Building on the production server:** secret exposure, non-reproducibility, fragility.
- **Local-only manual builds:** no audit trail, bus-factor of one, error-prone signing.
