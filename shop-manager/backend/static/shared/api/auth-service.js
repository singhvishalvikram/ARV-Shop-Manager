/** Auth service — signup/login/logout/me against `/api/v1/auth`. */
import { http, setToken } from './http-client.js';
import { API_BASE } from './env.js';

/** @param {{phone:string, password:string, name?:string}} payload */
export async function signup(payload) {
  const data = await http.post('/auth/signup', payload, { auth: false });
  if (data && data.token) setToken(data.token);
  return data;
}

/** @param {{phone:string, password:string}} payload */
export async function login(payload) {
  const data = await http.post('/auth/login', payload, { auth: false });
  if (data && data.token) setToken(data.token);
  return data;
}

export async function logout() {
  try {
    await http.post('/auth/logout');
  } finally {
    setToken(null);
  }
}

/** @returns {Promise<{user:object}>} */
export function me() {
  return http.get('/auth/me');
}

/**
 * URL that begins the Google Sign-In redirect flow. Send the browser here
 * (e.g. `location.href = googleLoginUrl()`); the backend bounces to Google and
 * back, landing on the configured success URL with `#token=...`.
 * @returns {string}
 */
export function googleLoginUrl() {
  return `${API_BASE}/auth/google/login`;
}

/**
 * After a Google redirect the session token arrives in the URL fragment
 * (`#token=...`). Capture it, persist it, and scrub it from the address bar so
 * it is not left in history. Returns true if a token was captured.
 * @returns {boolean}
 */
export function captureTokenFromFragment() {
  if (typeof window === 'undefined' || !window.location.hash) return false;
  const params = new URLSearchParams(window.location.hash.replace(/^#/, ''));
  const token = params.get('token');
  if (!token) return false;
  setToken(token);
  // Remove the fragment without reloading or adding a history entry.
  history.replaceState(null, '', window.location.pathname + window.location.search);
  return true;
}
