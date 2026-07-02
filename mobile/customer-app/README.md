# ARV Customer App (Android)

Native Java Android app for the customer catalog. Reads the **generated static JSON**
published by the existing pipeline to GitHub Pages — it touches **no admin API and no
databases**. Design, phase plan, and decisions: [`../../MOBILE-ARCHITECTURE.md`](../../MOBILE-ARCHITECTURE.md).

## What it does

- Browses the product catalog from `https://singhvishalvikram.github.io/Cinema123BW/data/*.json`
- Cache-then-network: instant cold start from disk cache; refetches only when
  `version.json → v` changes (same cache-bust contract as the PWA service worker)
- Search, category chips, featured/discount/out-of-stock badges — parity with the PWA
- Local cart (SharedPreferences, no PII, nothing leaves the device)
- WhatsApp checkout: pre-filled order enquiry to `settings.whatsapp_number`
- Full offline browsing once the catalog has loaded once

## Build

Requires **JDK 17** and the **Android SDK** (easiest: open `mobile/customer-app/`
in Android Studio, which supplies both).

```bash
cd mobile/customer-app

# One-time (wrapper jar is a binary and is generated, not hand-written):
gradle wrapper --gradle-version 8.7

./gradlew test                 # JVM unit tests (parser, cart, checkout, sync, filter)
./gradlew assembleDevDebug     # debug APK  → app/build/outputs/apk/dev/debug/
./gradlew assembleProdRelease  # release    (unsigned unless signing props are set)
```

Point a dev build at a local catalog server (e.g. `customer-view/site` on :3000):

```bash
./gradlew assembleDevDebug -PcatalogBaseUrl="http://10.0.2.2:3000"
```

## Signing (release)

Never commit keystores (repo `.gitignore` blocks them). Put credentials in
`~/.gradle/gradle.properties` on the build machine:

```
ARV_KEYSTORE_PATH=/absolute/path/arv-release.jks
ARV_KEYSTORE_PASS=…
ARV_KEY_ALIAS=arv
ARV_KEY_PASS=…
```

Generate once: `keytool -genkeypair -v -keystore arv-release.jks -alias arv -keyalg RSA -keysize 2048 -validity 10000`
Record the SHA-256 fingerprint — it is also needed for `assetlinks.json` if the TWA
route is ever revisited.

## Structure

```
app/src/main/java/com/arvshop/customer/
├── core/        ServiceLocator, AppExecutors, Result
├── data/
│   ├── model/   Product, Category, ShopSettings, CatalogVersion, Catalog, CartItem
│   ├── remote/  HttpClient, CatalogParser
│   ├── local/   DiskCache, CartStore
│   └── repo/    CatalogRepository, CartRepository, CatalogSync
├── ui/          catalog/ (Main), detail/, cart/   — MVVM, LiveData
└── util/        ImageLoader, WhatsAppCheckout, CartCalculator, CatalogFilter, Formats
app/src/test/    JVM unit tests + fixtures copied from real git-pages/data/
```

## Guardrails honored

- Reads only pipeline-generated JSON (never hand-edited, never written)
- Zero third-party runtime dependencies beyond AndroidX/Material (supply-chain minimalism)
- No secrets in source; signing via gradle properties; HTTPS-only (`usesCleartextTraffic=false`)
- White-label: shop title/currency/toggles come from `settings.json` at runtime
- Launcher icons are generated placeholders — replace with real branding before Play Store (Phase 20)
