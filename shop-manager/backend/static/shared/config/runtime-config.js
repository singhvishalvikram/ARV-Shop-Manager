/**
 * Runtime white-label configuration.
 *
 * Branding (name, theme, currency, contact) is loaded at runtime from the
 * PUBLIC `/api/v1/catalog/settings` endpoint and applied to the DOM — never
 * baked into the build. One shell, branded per shop from `settings` (ADR-002).
 *
 * No secrets here; `/catalog/settings` returns a public whitelist only.
 */
import { getCatalogSettings } from '../api/catalog-service.js';

// Generic, white-label-safe fallbacks (no tenant brand baked in).
const DEFAULTS = Object.freeze({
  app_title: 'Shop Manager',
  app_subtitle: 'Product Catalog',
  theme_color: '#2196F3',
  currency_symbol: '₹',
});

let _config = { ...DEFAULTS };

/** @returns {object} the last-applied config (defaults until `loadBranding`). */
export function getConfig() {
  return _config;
}

/**
 * Fetch shop settings and apply branding. Falls back to defaults on any error
 * so the app is never blocked by a branding fetch.
 * @returns {Promise<object>}
 */
export async function loadBranding() {
  try {
    const settings = await getCatalogSettings();
    _config = mergeNonEmpty(DEFAULTS, settings);
  } catch {
    _config = { ...DEFAULTS };
  }
  applyBranding(_config);
  return _config;
}

/** Apply branding to the document (title, theme color, brand text). */
export function applyBranding(cfg = _config) {
  if (typeof document === 'undefined') return;

  if (cfg.app_title) {
    document.title = cfg.app_title;
    document.querySelectorAll('[data-brand-title]').forEach((el) => {
      el.textContent = cfg.app_title;
    });
  }
  if (cfg.app_subtitle) {
    document.querySelectorAll('[data-brand-subtitle]').forEach((el) => {
      el.textContent = cfg.app_subtitle;
    });
  }
  if (cfg.theme_color) {
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', cfg.theme_color);
    document.documentElement.style.setProperty('--brand-color', cfg.theme_color);
  }
}

/** Overlay only non-empty known/extra keys onto the defaults. */
function mergeNonEmpty(defaults, settings) {
  const merged = { ...defaults };
  if (settings && typeof settings === 'object') {
    for (const [key, value] of Object.entries(settings)) {
      if (value !== '' && value != null) merged[key] = value;
    }
  }
  return merged;
}
