/** Items service — owner inventory CRUD against `/api/v1/items` (auth required). */
import { http } from './http-client.js';

/** @param {string} [search] @param {{signal?:AbortSignal}} [opts] */
export function listItems(search = '', opts = {}) {
  const qs = search ? `?search=${encodeURIComponent(search)}` : '';
  return http.get(`/items${qs}`, opts);
}

/** @param {number} id */
export function getItem(id) {
  return http.get(`/items/${id}`);
}

/** @param {object} item */
export function createItem(item) {
  return http.post('/items', item);
}

/** @param {number} id @param {object} patch */
export function updateItem(id, patch) {
  return http.put(`/items/${id}`, patch);
}

/** @param {number} id */
export function deleteItem(id) {
  return http.del(`/items/${id}`);
}
