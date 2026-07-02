# PRODUCT-ROADMAP.md — Making ARV Shop Manager Sellable

> **Version:** 2026-07-02 · **Risk tier:** Commercial/Production
> Governing standards: `GUARDRAILS.md`, `CODING_STANDARDS.md`, `pipeline_ops.md`,
> `prompt_design.md`. This is the path from "works end-to-end for one shop" to
> "a product you can sell to many shopkeepers."

---

## 0. Where we are today (honest baseline)

**Built & verified:**
- **Backend** (V01 FastAPI, `/api/v1`): auth (argon2id + sessions), items, sales,
  dashboard, settings, live catalog, cart. **75 pytest tests pass.**
- **Admin app** (native Java): login/signup, dashboard, inventory CRUD + camera, sales,
  sales history, curation, settings, offline queue. 12 unit + 4 Espresso tests pass;
  signed release APK builds.
- **Customer app** (native Java): catalog, search, cart, WhatsApp checkout, offline.
  25 unit tests; signed APK.
- **Customer PWA** (`git-pages/`): served live by the backend.

**Single-tenant.** One DB, one shop. This is the #1 thing between "demo" and "SaaS."

---

## 1. The sellability gap — what a real shop-manager SaaS needs

Grouped by theme, each with concrete tasks. Priority: **P0** = required before selling to
a second shop; **P1** = required to charge money; **P2** = growth/retention.

### 1.A Multi-tenancy (P0) — the foundation

The product is currently one-shop. To sell, every row must belong to a shop.

- [ ] **Postgres + `shop_id` + Row-Level Security** (per V01 `CLAUDE.md` §4 — *not*
  file-per-shop SQLite, *not* repo-per-shop). Migrate the schema; add `shops` table.
- [ ] Scope every query/route to the authenticated user's `shop_id` (middleware).
- [ ] `POST /api/v1/shops` (signup provisions a shop + owner atomically).
- [ ] Tenant isolation tests: shop A can never read/write shop B's items/sales/settings.
- [ ] Move product images to **object storage** (S3/GCS) + CDN; drop base64-in-DB.

### 1.B Onboarding & self-serve (P0/P1)

- [ ] In-app **shop setup wizard** (name, logo, WhatsApp, currency) after first signup.
- [ ] Owner **profile & password reset** (`/auth/forgot`, OTP via SMS — see security).
- [ ] Sample-data seeding on new shops so the app isn't empty on day one.
- [ ] Storefront URL per shop (`/s/<shop-slug>`), shareable; QR code generator in-app.

### 1.C Billing & plans (P1) — how you charge

- [ ] **Razorpay** (India-first) subscriptions; `subscriptions` table + webhooks.
- [ ] Plan gating: Free (e.g. 50 products, ARV branding) vs Pro (unlimited, custom
  domain, remove branding). Enforce server-side, not in the app.
- [ ] Grace period + dunning on failed payment; read-only mode when lapsed.
- [ ] In-app "Upgrade" screen; receipts/invoices.

### 1.D Owner value features (P1/P2) — why they'll pay & stay

- [ ] **Reports:** daily/weekly/monthly sales, best-sellers, profit (uses `purchase_cost`),
  low-stock alerts, dead stock. Charts in the admin app.
- [ ] **Barcode scanning** for fast add/lookup/sell (CameraX + ML Kit).
- [ ] **Bulk import/export** (CSV) of inventory.
- [ ] **Multiple staff** per shop with roles (owner/cashier) — extends the `role` column.
- [ ] **Customer orders** beyond WhatsApp: an in-storefront cart→order that lands in the
  admin app (`POST /api/v1/orders`, order status workflow). Push notification to owner.
- [ ] **Inventory adjustments & audit log** (damage, returns, stock-take).
- [ ] **GST/tax** fields and tax-inclusive pricing (India retail requirement).

### 1.E Storefront polish (P2)

- [ ] Custom domain per shop (Pro) + automatic HTTPS.
- [ ] Themes/branding, banner images, categories ordering.
- [ ] SEO basics (per-shop meta, sitemap), social share cards.
- [ ] Delivery/pickup options and basic order-status page for customers.

### 1.F Reliability & ops (P1)

- [ ] **Automated DB backup** with point-in-time recovery (managed Postgres or Litestream
  for SQLite) — replaces the fragile Drive-per-write script. Test restores.
- [ ] Health/uptime monitoring, error tracking (Sentry), structured logs already exist.
- [ ] Staging environment; **CI/CD** (see below); canary/rollback (pipeline_ops).

---

## 2. Security roadmap — what MUST be implemented before real customer data

Mapped to `GUARDRAILS.md`. The backend already does argon2id, server-side sessions,
parameterized SQL, rate-limited auth, universal envelope. Remaining, by priority:

### 2.A Before a second shop's data touches the system (P0 — non-negotiable)

- [ ] **Tenant authorization** (IDOR prevention, GUARDRAILS 2.4): every read/write checks
  `resource.shop_id == currentUser.shop_id`. This is the #1 security requirement of
  multi-tenancy — one missed check leaks another shop's data.
- [ ] **HTTPS everywhere** (1.3): TLS on the API host; HSTS; the app is already
  HTTPS-only in prod. No cleartext outside dev emulator loopback.
- [ ] **Secrets management** (1.4): `AUTH_SECRET` and all keys from a secret manager
  (Vault/AWS/GCP), not env files on the box. Rotate the dev keys before launch.
- [ ] **Production signing keystore** for both apps, stored in CI secrets — the current
  `~/.arv-keystore/` dev keystore must be rotated (pipeline_ops).

### 2.B Before charging money / handling PII at scale (P1)

- [ ] **CSRF** on cookie-based flows if any browser session is added (2.5); the mobile
  Bearer flow is not cookie-based, but the PWA/storefront may need it.
- [ ] **Security headers** on all responses (2.8): HSTS, X-Content-Type-Options,
  X-Frame-Options, CSP for the storefront/PWA.
- [ ] **Rate limiting** beyond auth: item/sale endpoints, per-shop quotas (DoS/abuse).
- [ ] **Input hardening**: file-upload magic-byte validation for images (3.1), max sizes
  (already 5 MB cap), SSRF guard if any user-supplied URL fetch is added (2.7).
- [ ] **SMS OTP / 2FA** for owner login and password reset (account-takeover defense).
- [ ] **Payment security**: never store card data; rely on Razorpay tokenization; verify
  webhook signatures.
- [ ] **Audit logging** of sensitive actions (login, price changes, deletes, payouts)
  with redaction of secrets/PII (6.4).
- [ ] **Dependency & container scanning** in CI (SCA/SAST — pipeline_ops Module 2);
  supply-chain minimalism already reduces the app's surface.

### 2.C Compliance (P2, market-dependent)

- [ ] **DPDP Act (India) / GDPR** basics: privacy policy, data export & delete, consent,
  retention limits (pipeline_ops 5.4). Customer PII is phone numbers + orders.
- [ ] PCI-DSS scope minimized by using a hosted payment provider.
- [ ] SOC2 posture (audit logs, access control, backups) if selling to larger merchants.

---

## 3. Engineering enablers (cross-cutting, do early)

- [ ] **CI/CD** (`pipeline_ops`): GitHub Actions — backend pytest + app `test` +
  `assembleDebug` on every PR to `mobile/**` / `shop-manager/**`; block on failure;
  SCA/secret scanning; artifact upload (APK/AAB).
- [ ] **Migrations** framework for Postgres (Alembic) with tested rollback (GUARDRAILS 6.1).
- [ ] **Instrumented test matrix**: expand Espresso (add-item, record-sale, offline) and
  run on a device farm; backend contract tests.
- [ ] **Observability**: dashboards for the RED metrics the backend already emits; alerts.
- [ ] **AAB + Play Store** release pipeline; internal testing track; privacy & data-safety
  declarations (admin app collects owner phone; customer app collects nothing).

---

## 4. Suggested sequence (thin slices, each shippable)

1. **CI/CD + migrations + backups** (enablers; makes everything after safe).
2. **Multi-tenancy + tenant authorization + object storage** (P0 core + its #1 security).
3. **Onboarding wizard + storefront-per-shop + QR share** (first real sellable loop).
4. **Billing (Razorpay) + plan gating** (turn it on for money).
5. **Reports + barcode + orders-beyond-WhatsApp** (owner value → retention).
6. **OTP/2FA, security headers, rate limits, audit log** (harden as data grows).
7. **Compliance (DPDP/GDPR), custom domains, staff roles** (scale to larger merchants).

Do **not** build multi-tenant until at least one real shop is using the single-tenant
build (per V01 `CLAUDE.md` §4 — validate demand first). But item 1 (enablers) and the
security P0 list are worth doing regardless.
