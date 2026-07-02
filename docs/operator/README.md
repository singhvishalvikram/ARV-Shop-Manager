# Operator Guide — your part to take this live

These docs cover the things **only you can do** (run a browser, create cloud
accounts, provision a host). The engineering work is done and verified at the
test/HTTP level (75 backend tests, lint, secret + dependency + white-label gates),
but a few human steps remain before going live.

Read in order:

1. **[01-run-and-verify.md](01-run-and-verify.md)** — run it locally and click through
   the owner app + catalog (the browser checks I can't run here).
2. **[02-google-oauth-setup.md](02-google-oauth-setup.md)** — create the Google OAuth
   client so "Sign in with Google" works.
3. **[03-deployment.md](03-deployment.md)** — deploy the one FastAPI service (HTTPS, env,
   both faces, images, DB, catalog hosting).
4. **[04-action-items.md](04-action-items.md)** — the consolidated checklist: what blocks
   what, and the order to do it.

Reference: [`../mobile-roadmap.md`](../mobile-roadmap.md) (phase status),
[`../security/threat-model.md`](../security/threat-model.md),
[`../observability/slos.md`](../observability/slos.md), and the architecture decisions in
[`../architecture/`](../architecture/).

> When something misbehaves, grab the **`X-Request-ID`** from the response (or the
> "Reference" id shown on a server error) and the matching JSON log line — that pair makes
> any issue traceable.
