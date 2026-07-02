/** Settings service — owner full settings against `/api/v1/settings` (auth required). */
import { http } from './http-client.js';

export function getSettings() {
  return http.get('/settings');
}

/** @param {object} patch key/value settings to upsert */
export function updateSettings(patch) {
  return http.post('/settings', patch);
}
