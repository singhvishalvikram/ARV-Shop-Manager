# DEVICE-INSTALL.md — Will the APK run on my phone, and how?

> **Version:** 2026-07-02 · **Risk tier:** Commercial/Production
> Short answer: **Yes — but it depends on which app, and the admin app needs a
> reachable backend.** This page tells you exactly what to do and what won't work.

---

## 1. Which Android versions are supported?

Both apps are built with the same SDK levels:

| Setting | Value | Meaning |
|---|---|---|
| `minSdk` | **24** | Runs on **Android 7.0 (Nougat)** and newer |
| `targetSdk` | **34** | Tested against **Android 14** behaviour |
| `compileSdk` | 34 | Built with the Android 14 SDK |

**So: any phone on Android 7.0 or later runs these APKs** — that's roughly 99% of active
Android handsets in India today, including low-cost devices. Nothing below Android 7.0
(2016) is supported. Newer versions (Android 15/16) run them fine via forward
compatibility; bump `targetSdk` later to adopt new-version behaviour.

---

## 2. The two apps behave very differently on a real phone

### 2.1 Customer app — works standalone, install and go ✅

- Reads its product catalog as **static JSON from GitHub Pages**
  (`https://singhvishalvikram.github.io/Cinema123BW`), then checks out via **WhatsApp**.
- **No backend, no login, no server** needed. Install the APK on any Android 7.0+ phone
  with internet and it works immediately.
- The catalog URL is baked in at build time. To point it at a different published
  catalog: `./gradlew assembleProdRelease -PcatalogBaseUrl="https://<your-pages-origin>"`.

### 2.2 Admin (owner) app — needs a reachable backend ⚠️

The admin app is a **client of the FastAPI backend** (`shop-manager/backend`, `/api/v1`).
Without a backend it can reach, **login and every screen will fail with a network error.**

The backend URL is **baked into the APK at build time** (it is not configurable in-app, by
design — no server picker UI to misconfigure). Which URL you build with decides where it
will and won't work:

| You built with… | Works on emulator? | Works on a real phone? |
|---|---|---|
| **default `dev`** → `http://10.0.2.2:8000` | ✅ (that's the emulator's alias for your PC) | ❌ `10.0.2.2` is meaningless on a real phone |
| **`dev` + `-PapiBaseUrl=http://<PC-LAN-IP>:8000`** | ✅ | ✅ **only while on the same Wi-Fi** as the PC running the backend |
| **`prod` + `-PapiBaseUrl=https://<your-domain>`** | ✅ | ✅ **anywhere** (this is the real, sellable build) |

> The `dev` flavor allows plaintext HTTP (`ALLOW_CLEARTEXT=true`) for LAN testing only.
> The `prod` flavor is **HTTPS-only** (`usesCleartextTraffic=false`) — it will refuse a
> plain-HTTP URL. That is deliberate: real customer data must travel over TLS.

**The APK you were running was almost certainly the default `dev` build → `10.0.2.2:8000`,
which is why it works in the emulator but cannot log in on your physical phone.** To use it
on your phone, rebuild with a URL your phone can actually reach (next section).

---

## 3. Install the APK on a physical phone (sideload)

Google Play is not involved yet, so you install the `.apk` file directly:

1. **Build a phone-appropriate APK** (see §2.2 for the admin app):
   ```bash
   cd mobile/admin-app
   # Same-Wi-Fi LAN test build (replace with your PC's IP, e.g. 192.168.1.5):
   ./gradlew assembleDevDebug -PapiBaseUrl="http://192.168.1.5:8000"
   #   → app/build/outputs/apk/dev/debug/app-dev-debug.apk
   ```
   For the customer app: `cd mobile/customer-app && ./gradlew assembleProdRelease`.

2. **Get the APK onto the phone** — USB cable + `adb install <path>.apk`, or upload it to
   Drive/WhatsApp-to-self and download it on the phone.

3. **Allow install from this source.** Android blocks unknown-source installs by default.
   When you tap the APK, it prompts *"Allow this source to install apps"* → enable it for
   your browser/Files app (Settings → Apps → Special access → Install unknown apps).

4. **Tap the APK → Install → Open.**
   - Customer app: browse and checkout right away.
   - Admin app: log in with the seeded demo owner **`9999999999` / `owner1234`** (only if
     the backend it was built against is running and reachable), or **Create a shop
     account**.

### Signed vs unsigned
- `assembleDevDebug` is signed with the **debug** key — fine for sideloading to your own
  phone, **not** for Play Store.
- `assembleProdRelease` is signed with the **release keystore** (R8-minified). See
  [`DEPLOYMENT.md`](DEPLOYMENT.md) §3 for release signing and the Play Store path.

---

## 4. Quick decision guide

- **"I just want to show the storefront to customers."** → Install the **customer app**
  APK. Done. No backend.
- **"I want to test the owner app end-to-end on my phone, at home."** → Run the backend on
  your PC, build the admin app with `-PapiBaseUrl=http://<PC-LAN-IP>:8000`, keep the phone
  on the same Wi-Fi.
- **"I want the owner app to work anywhere, for a real shop."** → Deploy the backend behind
  HTTPS (see [`DEPLOYMENT.md`](DEPLOYMENT.md)), build `assembleProdRelease` with
  `-PapiBaseUrl=https://<your-domain>`, then sideload (or publish to Play).

See [`DEPLOYMENT.md`](DEPLOYMENT.md) for the full backend + distribution walkthrough.
