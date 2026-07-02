/**
 * Environment / runtime configuration for the front-end.
 *
 * The API base URL is resolved in this order (first match wins):
 *   1. `window.__API_BASE__`         — set inline before this module loads.
 *   2. `<meta name="api-base">`      — set in the page <head> (best for static hosts
 *                                      like GitHub Pages that call a cross-origin API).
 *   3. same-origin `/api/v1`         — default when the API and the page share an origin.
 *
 * No secrets ever live here (Enterprise Coding Standards §3.9). This is public config.
 */

const DEFAULT_API_PREFIX = '/api/v1';

/** @returns {string} the API base URL without a trailing slash. */
export function resolveApiBase() {
  if (typeof window !== 'undefined') {
    if (typeof window.__API_BASE__ === 'string' && window.__API_BASE__) {
      return stripTrailingSlash(window.__API_BASE__);
    }
    const meta = document.querySelector('meta[name="api-base"]');
    const fromMeta = meta && meta.getAttribute('content');
    if (fromMeta) {
      return stripTrailingSlash(fromMeta);
    }
  }
  return DEFAULT_API_PREFIX;
}

/** @param {string} url @returns {string} */
function stripTrailingSlash(url) {
  return url.endsWith('/') ? url.slice(0, -1) : url;
}

export const API_BASE = resolveApiBase();
