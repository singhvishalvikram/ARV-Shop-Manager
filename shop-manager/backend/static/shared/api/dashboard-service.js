/** Dashboard service — owner stats against `/api/v1/dashboard` (auth required). */
import { http } from './http-client.js';

/** @param {{signal?:AbortSignal}} [opts] */
export function getDashboard(opts = {}) {
  return http.get('/dashboard', opts);
}
