/**
 * Catalog service — PUBLIC customer-facing data against `/api/v1/catalog`.
 * No auth. Backend guarantees only customer-safe fields (never purchase_cost,
 * location, or raw quantity).
 */
import { http } from './http-client.js';

/** @param {{signal?:AbortSignal}} [opts] */
export function listProducts(opts = {}) {
  return http.get('/catalog/products', { ...opts, auth: false });
}

/** @param {{signal?:AbortSignal}} [opts] */
export function listCategories(opts = {}) {
  return http.get('/catalog/categories', { ...opts, auth: false });
}

/** @param {{signal?:AbortSignal}} [opts] */
export function getCatalogSettings(opts = {}) {
  return http.get('/catalog/settings', { ...opts, auth: false });
}
