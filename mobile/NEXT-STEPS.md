# NEXT-STEPS.md — What to do next, in order

> **Version:** 2026-07-02 · **Risk tier:** Commercial/Production
> The exhaustive plan is [`PRODUCT-ROADMAP.md`](PRODUCT-ROADMAP.md). **This** page is the
> short, sequenced answer to "what do we actually do next?" — each step is shippable on its
> own, ordered so nothing later is blocked by something we skipped.

---

## Where we are (one honest line)
Everything works **end-to-end for one shop**: backend (75 tests), admin app (12 unit + 4
Espresso, signed APK), customer app (25 tests, signed APK), and the PWA. It is
**single-tenant** and **not yet deployed to a public HTTPS host**. Those two facts define
the next steps.

---

## Step 0 — Get it onto a real phone for a real pilot (days)
The fastest way to learn is one real shopkeeper using it.
1. Deploy the backend to a small HTTPS host (Railway/Render/Fly/VPS + Caddy) —
   [`DEPLOYMENT.md`](DEPLOYMENT.md) §1.
2. Build `assembleProdRelease -PapiBaseUrl="https://<domain>"` and sideload —
   [`DEVICE-INSTALL.md`](DEVICE-INSTALL.md).
3. Have one owner run their real inventory + sales for a week. Watch, don't build.

## Step 1 — Engineering enablers (do before multi-tenant; ~1 week)
These make everything after safe. From ROADMAP §3.
- [ ] **CI/CD** (GitHub Actions): backend pytest + app `test`/`assembleDebug` on every PR;
      block on failure; secret + dependency scan; upload APK/AAB artifacts.
- [ ] **Migrations framework** (Alembic) with tested rollback — needed the moment the schema
      changes.
- [ ] **Automated DB backup + tested restore** (managed Postgres PITR or Litestream).
- [ ] **Fresh production signing keystore** in CI secrets; retire the local dev keystore.

## Step 2 — Multi-tenancy + its #1 security control (the sellability unlock; ROADMAP §1.A/§2.A)
Only start once a pilot shop is actively using Step 0. This is what lets you sell to a
**second** shop.
- [ ] Postgres + `shops` table + `shop_id` on every row + **Row-Level Security**.
- [ ] Scope every query/route to the caller's `shop_id` (middleware).
- [ ] **Tenant authorization / IDOR guard**: every read+write asserts
      `resource.shop_id == currentUser.shop_id` (one miss leaks another shop's data).
- [ ] Tenant-isolation tests: shop A can never see shop B.
- [ ] Product images → object storage (S3/GCS) + CDN; drop base64-in-DB.

## Step 3 — First sellable loop (ROADMAP §1.B)
- [ ] In-app shop setup wizard (name, logo, WhatsApp, currency) after signup.
- [ ] Password reset via SMS OTP.
- [ ] Storefront URL per shop (`/s/<slug>`) + in-app QR share.

## Step 4 — Turn on money (ROADMAP §1.C)
- [ ] Razorpay subscriptions + webhooks (verify signatures).
- [ ] Plan gating enforced **server-side** (Free vs Pro); read-only when lapsed.

## Step 5 — Owner value → retention (ROADMAP §1.D)
- [ ] Reports (sales trends, best-sellers, profit from `purchase_cost`, low/dead stock).
- [ ] Barcode scanning (CameraX + ML Kit); CSV import/export.
- [ ] Customer orders beyond WhatsApp (`POST /orders` → admin app + push).

## Step 6 — Harden as data grows (ROADMAP §2.B)
- [ ] OTP/2FA on owner login; security headers (HSTS/CSP) on PWA; per-shop rate limits.
- [ ] Audit logging of sensitive actions (login, price change, delete, payout).
- [ ] SCA/SAST in CI; image upload magic-byte validation.

## Step 7 — Scale & compliance (ROADMAP §2.C/§1.E)
- [ ] DPDP (India)/GDPR: privacy policy, data export/delete, retention limits.
- [ ] Custom domains (Pro), staff roles (owner/cashier), themes.

---

### Immediate technical debt to clear (small, do anytime)
- [ ] Rotate the dev keystore; move `AUTH_SECRET` to a secret manager (not `.env` on the box).
- [ ] Expand Espresso (add-item, record-sale, offline) + a device-farm run.
- [ ] Bump `targetSdk` as new Android versions land; keep `minSdk 24` (Android 7.0) for reach.

**Golden rule (from `CLAUDE.md` §4): do not build multi-tenant until at least one real shop
is using the single-tenant build.** Steps 0 and 1 are safe to do now regardless.
