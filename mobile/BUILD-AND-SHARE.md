# BUILD-AND-SHARE.md — What got built, where, and how to actually use it

> **Version:** 2026-07-02 · **Risk tier:** Commercial/Production
> This is the concrete, "run these exact commands, here's the file, here's how it works"
> companion to [`DEVICE-INSTALL.md`](DEVICE-INSTALL.md) (Android-version/sideload reference)
> and [`DEPLOYMENT.md`](DEPLOYMENT.md) (full HTTPS production deploy).

---

## 1. What was built, and the exact paths

| App | Command | Output path | Size |
|---|---|---|---|
| **Customer app** | `cd mobile/customer-app && ./gradlew assembleProdDebug` | `mobile/customer-app/app/build/outputs/apk/prod/debug/app-prod-debug.apk` | ~6.0 MB |
| **Admin app** | `cd mobile/admin-app && ./gradlew assembleDevDebug -PapiBaseUrl="http://127.0.0.1:8000"` | `mobile/admin-app/app/build/outputs/apk/dev/debug/app-dev-debug.apk` | ~6.9 MB |

Both are **debug-signed** (Android's built-in debug key — no keystore password needed).
That's deliberate and correct for this stage: debug signing is exactly right for
sideloading to your own phone; it is **not** valid for the Play Store (see §5).
Both builds passed their unit test suites (`./gradlew test`) before packaging.

APKs are build output, not source — they are **git-ignored** (see each app's
`.gitignore`) and were **not** pushed to GitHub. Only this documentation was pushed. To get
the file itself onto your phone, copy it directly (USB, AirDrop-to-Android-via-Drive/
WhatsApp-to-self, etc.) — see §3.

---

## 2. How each app actually works

### 2.1 Customer app — fully standalone, works anywhere immediately
```
Phone → GitHub Pages (static product JSON) → cart (on-device) → tap "Order" → WhatsApp
```
No backend, no login, no account. Install the APK on any Android 7.0+ phone with internet
and it works the moment you open it. This is the one that is **freely shareable** — send
the APK file to anyone and it works on their phone too, unmodified.

### 2.2 Admin app — needs the backend, and *this specific build* needs a USB cable

This build's API URL is baked in as `http://127.0.0.1:8000` (the phone's own loopback
address) — that only means something once you map it to your Mac's backend over USB:

```
Phone (USB cable) ──adb reverse──► Mac's 127.0.0.1:8000 ──► FastAPI backend ──► shop.db
```

`adb reverse tcp:8000 tcp:8000` tells Android: "when an app on this phone requests
`127.0.0.1:8000`, tunnel it over the USB cable to port 8000 on the computer it's plugged
into." The app itself has no idea a cable is involved — it just calls `127.0.0.1:8000` like
it would on an emulator. This is why the loopback address (not your LAN IP) was used: it's
**already whitelisted** for cleartext HTTP in `network_security_config.xml`, so no security
file had to be touched and nothing is exposed to the Wi-Fi network at all — traffic never
leaves the cable.

**This only works while:**
- the phone is plugged into **this Mac** via USB with USB debugging authorized, **and**
- `adb reverse tcp:8000 tcp:8000` has been run for that connection, **and**
- the backend is running on this Mac (`shop-manager/backend`, port 8000).

Unplug the cable, or restart adb, and it stops working until you redo the `adb reverse`
step. **This is a local testing build, not a shareable one** — see §4 for what "shareable"
actually requires.

---

## 3. Install steps (do this now, on your phone)

1. **Enable USB debugging** on the phone (Settings → About phone → tap "Build number" 7×
   to unlock Developer Options → enable **USB debugging**).
2. **Plug the phone into this Mac** via USB. Accept the "Allow USB debugging?" prompt on
   the phone.
3. **Confirm the Mac sees it:**
   ```bash
   ~/Library/Android/sdk/platform-tools/adb devices
   #   should list your device (not empty)
   ```
4. **Map the port** (repeat this any time you reconnect the cable):
   ```bash
   ~/Library/Android/sdk/platform-tools/adb reverse tcp:8000 tcp:8000
   ```
5. **Make sure the backend is running** on this Mac:
   ```bash
   cd shop-manager/backend && source .venv/bin/activate
   AUTH_SECRET=devsecret_local uvicorn app.main:app --host 0.0.0.0 --port 8000
   ```
6. **Install both APKs** straight from the Mac over the same cable:
   ```bash
   adb install -r mobile/customer-app/app/build/outputs/apk/prod/debug/app-prod-debug.apk
   adb install -r mobile/admin-app/app/build/outputs/apk/dev/debug/app-dev-debug.apk
   ```
7. **Open the admin app**, log in with the seeded demo owner:
   ```
   Phone: 9999999999   Password: owner1234
   ```
   (Seeded fresh in this clone's `shop.db` — this file is git-ignored, so it's local-only
   and won't exist again after a fresh clone without re-seeding. Four demo products —
   milk, biscuits, salt, toothpaste — are already in there so the dashboard isn't empty.)
8. **Open the customer app** — no login, browse immediately, "Order via WhatsApp" at checkout.

If `adb install` isn't convenient, you can instead copy the `.apk` file to the phone
(WhatsApp-to-self, Drive, a cable file-transfer) and tap it to install — see
[`DEVICE-INSTALL.md`](DEVICE-INSTALL.md) §3 for the "allow unknown sources" prompt. The
admin app still needs the `adb reverse` step done first regardless of how the file itself
got onto the phone, since that's what makes `127.0.0.1:8000` mean anything.

---

## 4. "Can we share this?" — the honest answer, by audience

| Who | Customer app | Admin app |
|---|---|---|
| **You, right now, on your own phone** | ✅ send the APK, install, done | ✅ but only with the USB+`adb reverse` dance above, and only while your Mac + backend are on |
| **A friend testing it on their phone, in the same room** | ✅ no dependency on you at all | ❌ this build won't work for them — it's pinned to *your* Mac via USB |
| **A shopkeeper you want to pilot this with, anywhere** | ✅ already works | ❌ needs the backend deployed somewhere they can reach — see §4.1 |

### 4.1 What "sharable" for the admin app actually requires
The admin app can only be as reachable as the backend it points to. Three tiers, same as
`DEPLOYMENT.md` §1.4:

1. **What we did today (USB loopback):** zero setup cost, zero exposure, but tied to one
   cable, one Mac, one session. Good for showing yourself the flow works.
2. **Deploy the backend behind HTTPS** (a $5–10/month VPS or Railway/Render/Fly + a domain
   + Caddy for automatic TLS — `DEPLOYMENT.md` §1), then build:
   ```bash
   ./gradlew assembleProdRelease -PapiBaseUrl="https://api.yourshop.com" \
     -PARV_KEYSTORE_PATH=... -PARV_KEYSTORE_PASS=... -PARV_KEY_ALIAS=... -PARV_KEY_PASS=...
   ```
   That APK works **from anywhere with internet**, no cable, no same-Wi-Fi requirement —
   send it to any shopkeeper. This is the real "sharable" admin build, and it's the only
   one appropriate to hand to someone else, because production data should never ride on
   a temporary dev backend anyway.
3. **A temporary public tunnel** (e.g. `cloudflared tunnel --url http://localhost:8000`) can
   preview an admin build over the internet without a full deploy — **but this exposes the
   backend, its dev `AUTH_SECRET`, and the seeded demo login to the public internet for as
   long as the tunnel runs.** We did not set this up by default; if you want a quick
   internet-reachable demo, say so explicitly and we'll do it deliberately (fresh secret,
   understand it's temporary, tear it down after).

---

## 5. Before this goes further than your own phone

- These are **debug-signed** builds. Sideloading to your own device: fine. Handing an APK
  to someone else long-term, or Play Store: needs a **release** build signed with the
  production keystore (`DEPLOYMENT.md` §3) — that keystore, per prior notes, still needs to
  be rotated from the current dev-only one before real distribution.
- The seeded demo login (`9999999999` / `owner1234`) is for **your own testing only** —
  never ship a build with known demo credentials to an actual shop owner; give them the
  in-app **"Create a shop account"** flow instead.
