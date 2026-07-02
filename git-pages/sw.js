/*
 * Unified service worker — catalog face. Same strategy as the owner app
 * (shop-manager/backend/static/js/sw.js); kept identical by design.
 * This file sits at the site root so its scope is "/". Bump CACHE_VERSION to
 * invalidate.
 *
 * Strategy: non-GET not handled; /api/ never cached; navigations network-first
 * with offline fallback; static assets stale-while-revalidate.
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

self.addEventListener('install', function () { self.skipWaiting(); });

self.addEventListener('activate', function (event) {
  event.waitUntil((async function () {
    const names = await caches.keys();
    await Promise.all(names.filter(function (n) { return n !== CACHE_NAME; })
      .map(function (n) { return caches.delete(n); }));
    await self.clients.claim();
  })());
});

self.addEventListener('fetch', function (event) {
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
    .then(function (res) {
      if (res && res.status === 200) cache.put(req, res.clone());
      return res;
    })
    .catch(function () { return cached; });
  return cached || network;
}
