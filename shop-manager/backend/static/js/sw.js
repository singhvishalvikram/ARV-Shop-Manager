/*
 * Unified service worker (owner + catalog faces share this strategy).
 * Classic SW for broad browser support. Bump CACHE_VERSION to invalidate.
 *
 * Strategy:
 *   - Non-GET            -> not handled (mutations must always hit the network).
 *   - /api/ requests     -> NOT handled / never cached. API responses are
 *                           per-session and must be fresh; the app has its own
 *                           IndexedDB/localStorage fallback when offline.
 *   - Navigations (HTML) -> network-first, fall back to cache, then an offline page.
 *   - Static assets      -> stale-while-revalidate (fast, refreshed in background).
 *
 * NOTE: register from the ORIGIN ROOT (/sw.js) so the scope is "/" and the SW
 * controls the whole app — a SW served from /static/js/ only scopes /static/js/.
 */
const CACHE_VERSION = 'v5';
const CACHE_NAME = 'shop-pwa-' + CACHE_VERSION;

const OFFLINE_HTML =
  '<!doctype html><meta charset="utf-8">' +
  '<meta name="viewport" content="width=device-width,initial-scale=1">' +
  '<title>Offline</title>' +
  '<body style="font-family:system-ui,sans-serif;text-align:center;padding:48px 24px;color:#444">' +
  '<h1>You’re offline</h1>' +
  '<p>Reconnect to load fresh data. Cached content may still be available.</p>';

self.addEventListener('install', () => self.skipWaiting());

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    const names = await caches.keys();
    await Promise.all(names.filter((n) => n !== CACHE_NAME).map((n) => caches.delete(n)));
    await self.clients.claim();
  })());
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.pathname.includes('/api/')) return; // never cache API responses

  if (req.mode === 'navigate') {
    event.respondWith(networkFirst(req));
  } else {
    event.respondWith(staleWhileRevalidate(req));
  }
});

async function networkFirst(req) {
  const cache = await caches.open(CACHE_NAME);
  try {
    const res = await fetch(req);
    if (res && res.status === 200) cache.put(req, res.clone());
    return res;
  } catch (err) {
    const cached = await cache.match(req);
    return cached || new Response(OFFLINE_HTML, {
      status: 503,
      headers: { 'Content-Type': 'text/html; charset=utf-8' },
    });
  }
}

async function staleWhileRevalidate(req) {
  const cache = await caches.open(CACHE_NAME);
  const cached = await cache.match(req);
  const network = fetch(req)
    .then((res) => {
      if (res && res.status === 200) cache.put(req, res.clone());
      return res;
    })
    .catch(() => cached);
  return cached || network;
}
