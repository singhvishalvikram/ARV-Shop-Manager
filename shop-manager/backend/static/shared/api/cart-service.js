/** Cart service — per-authenticated-user cart against `/api/v1/cart`. */
import { http } from './http-client.js';

export function getCart() {
  return http.get('/cart');
}

/** @param {number} itemId @param {number} qty */
export function addToCart(itemId, qty) {
  return http.post('/cart', { item_id: itemId, qty });
}

/** @param {number} itemId */
export function removeFromCart(itemId) {
  return http.del(`/cart/${itemId}`);
}
