/** Sales service — owner sales against `/api/v1/sales` (auth required). */
import { http } from './http-client.js';

/** @param {{item_id:number, quantity_sold:number, sale_price?:number, description?:string}} sale */
export function createSale(sale) {
  return http.post('/sales', sale);
}

/** @param {string} [date] ISO date filter @param {{signal?:AbortSignal}} [opts] */
export function listSales(date = '', opts = {}) {
  const qs = date ? `?date=${encodeURIComponent(date)}` : '';
  return http.get(`/sales${qs}`, opts);
}
