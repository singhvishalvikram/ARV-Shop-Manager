# 1. Run locally & verify in a browser

I can verify the backend (75 tests, HTTP-level), but **I cannot drive a browser** in this
environment. These are the click-through checks only you can do. ~15 minutes.

## Run it

```bash
cd shop-manager/backend
python3.11 -m venv .venv && . .venv/bin/activate     # Python 3.11 recommended
pip install -r requirements.txt -r requirements-dev.txt
uvicorn app.main:app --port 8080
```

Optional sanity check before the browser:

```bash
python -m pytest -q          # expect: 75 passed
ruff check app domain.py tests
```

Open **http://localhost:8080/** (owner app) and **http://localhost:8080/catalog/** (catalog).

> Camera and "install app" need a **secure context**. `localhost` counts as secure, so they
> work locally; on a real device they need HTTPS (see [03-deployment.md](03-deployment.md)).

## Owner app checklist (http://localhost:8080/)

| # | Step | Expected |
|---|------|----------|
| 1 | First load shows the **login screen** (not the app) | Phone + password + "Sign in with Google" |
| 2 | Click "Create an owner account", enter a phone + password (≥8 chars), submit | Lands in the app (Dashboard) |
| 3 | Reload the page | Stays logged in (session token persists) |
| 4 | **Add Item** tab → fill name/type/price/qty → Add | "Item added"; appears in Inventory |
| 5 | Add an item **with a photo** (Start Camera → Capture → Use → Add) | Item saved **with image** (Phase 8) |
| 6 | Open an item → **Edit**, change price → Save | Updated in list + dashboard |
| 7 | **Sales** → search the item → record a sale | "Sale recorded"; stock decremented |
| 8 | Record a sale with qty **greater than stock** | Alert: "Not enough stock. Available: N" |
| 9 | Dashboard | Totals incl. **Stock Value (MRP)** and **Low Stock** list populated |
| 10 | Drawer → **Logout** | Back to the login screen |
| 11 | Try a wrong password 6× quickly | 6th attempt is blocked (rate-limited) |
| 12 | DevTools → Application → **Manifest / Service Worker** | Manifest has real PNG icons; SW scope is `/` |
| 13 | "Install app" (Chrome) | Installs as a standalone PWA |
| 14 | DevTools → Network → **Offline**, reload | App shell still loads (offline page if uncached) |

## Catalog checklist (http://localhost:8080/catalog/)

| # | Step | Expected |
|---|------|----------|
| 1 | Catalog loads | Products shown (from the live API; falls back to static JSON if API down) |
| 2 | A product's image | Loads (owner-uploaded image via the API) |
| 3 | Out-of-stock / discount items | "Out of Stock" / "% OFF" badges |
| 4 | Add to cart → cart → **WhatsApp Order** | Opens WhatsApp with the order text |
| 5 | Confirm **no cost data leaks** (DevTools → Network → `/api/v1/catalog/products`) | No `purchase_cost`, `location`, or raw `quantity` |

## If something is wrong

- Copy the **`X-Request-ID`** response header (DevTools → Network → the failing request →
  Response Headers), or the "Reference" id on a 500 page.
- Grab the matching backend JSON log line (same `request_id`).
- Send me both — that pair pinpoints the issue.

## Known-deferred (don't be alarmed)

- **Google sign-in** needs the OAuth client first — see [02-google-oauth-setup.md](02-google-oauth-setup.md).
- The **catalog's login/cart sync** still uses the legacy endpoints (degrades gracefully;
  local cart + WhatsApp checkout work). Its Bearer cutover is deployment-gated.
