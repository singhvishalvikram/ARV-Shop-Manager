# ARV Manager — Android Admin (Shop Owner) App

Native Java Android app for shop owners: inventory, sales, dashboard, storefront
curation, and settings. A **secure, authenticated client** of the consolidated V01
FastAPI backend (`/api/v1`, argon2id + Bearer sessions). No shortcuts — Commercial tier.

- **Design & plan:** [`../ADMIN-APP-BLUEPRINT.md`](../ADMIN-APP-BLUEPRINT.md)
- **Web→mobile parity + test evidence:** [`../MIGRATION-VERIFICATION.md`](../MIGRATION-VERIFICATION.md)
- **Future / sellability / security roadmap:** [`../PRODUCT-ROADMAP.md`](../PRODUCT-ROADMAP.md)

## Screens

| Screen | Purpose | Backend |
|--------|---------|---------|
| Login / **Sign up** | Owner auth (phone + password) | `POST /auth/login`, `POST /auth/signup` |
| Dashboard | Totals, stock value/MRP, today's revenue, low-stock | `GET /dashboard` |
| Inventory | List/search, add/edit/delete, camera photo | `GET/POST/PUT/DELETE /items` |
| Record Sale | Sell an item; stock decremented server-side | `POST /sales` |
| Sales History | Recent sales + total revenue | `GET /sales` |
| Settings | White-label (name, WhatsApp, currency, location) | `GET/POST /settings` |

Merchandising (show/hide, feature, badge) is set on the item edit screen. Because the
backend's `ItemCreate` schema omits merchandising, a **new** item with non-default
merchandising is created then patched with a follow-up `PUT` (verified against the API).

## Run it locally (owner flow end-to-end)

**1. Start the backend** (from `shop-manager/backend/`, in a venv with
`fastapi uvicorn argon2-cffi pydantic pillow`):

```bash
export AUTH_SECRET=devsecret_local          # required; any non-empty value for dev
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

**2. Build & install the app** (JDK 17 + Android SDK, or open in Android Studio):

```bash
cd mobile/admin-app
gradle wrapper --gradle-version 8.7          # one-time (generates the wrapper jar)
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
./gradlew installDevDebug                    # installs on a running emulator/device
```

The **dev** flavor points at `http://10.0.2.2:8000` (the Android emulator's alias for
your host machine). For a physical device, pass your host IP:
`./gradlew assembleDevDebug -PapiBaseUrl="http://192.168.1.x:8000"`.

**3. Log in.** A demo owner is seeded in the default `shop.db`:

```
Phone:    9999999999
Password: owner1234
```

Or tap **"Create a shop account"** to sign up a new owner.

## Test

```bash
./gradlew test                       # 12 JVM unit tests (envelope, models, validation, money)
./gradlew connectedDevDebugAndroidTest   # 4 Espresso tests (needs a booted emulator;
                                         # the live-login E2E also needs the backend on :8000)
```

Espresso coverage: login renders, empty-submit validation, signup navigation, and a
**live login→dashboard E2E** against the running backend.

## Build a release

```bash
./gradlew assembleProdRelease \
  -PARV_KEYSTORE_PATH="$HOME/.arv-keystore/arv-release.jks" \
  -PARV_KEYSTORE_PASS=… -PARV_KEY_ALIAS=… -PARV_KEY_PASS=…
```

`prod` flavor requires an HTTPS `apiBaseUrl` (`usesCleartextTraffic=false`). Never
commit the keystore (git-ignored). Rotate to a fresh production keystore before Play
Store (the dev keystore at `~/.arv-keystore/` is local-only).

## Architecture

```
core/         App, ServiceLocator, AppExecutors, Result
data/
  model/      AuthUser, Item, DashboardStats, Sale, SalesHistory
  remote/     ApiClient (Bearer, envelope, 401→logout), Envelope, ApiException
  local/      SessionStore (EncryptedSharedPreferences), OfflineQueue
  repo/       Auth, Inventory, Dashboard, Sales, Settings
ui/           auth/ (Login, Signup) · dashboard/ · inventory/ · item/ · sales/ · settings/
util/         ImageEncoder, Money, ItemValidator
```

MVVM (Activity → ViewModel → Repository → ApiClient/SessionStore/OfflineQueue), manual
DI, **AndroidX/Material + security-crypto only** — no Retrofit/OkHttp/Gson/Glide
(supply-chain minimalism, GUARDRAILS 6.2).

## Security posture

- Bearer token in `EncryptedSharedPreferences`; never logged; cleared on 401.
- HTTPS-only in prod; cleartext limited to emulator loopback in dev.
- Server-side validation is authoritative; client `ItemValidator` mirrors it for UX.
- Offline item creation uses an `Idempotency-Key` so retries never duplicate.
