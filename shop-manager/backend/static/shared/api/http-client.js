/**
 * HTTP client — the ONLY place the front-end calls `fetch` (Enterprise Coding
 * Standards §3.3). Every resource service goes through here.
 *
 * Responsibilities:
 *   - Prefix requests with the resolved API base (`env.js`).
 *   - Attach the bearer session token when present.
 *   - Unwrap the Universal Response Envelope `{success, data, error}` and return `data`.
 *   - Throw a typed `ApiError` (carrying the registry code) on failure.
 *   - Support cancellation via `AbortSignal`.
 */

import { API_BASE } from './env.js';

const TOKEN_STORAGE_KEY = 'session_token';

/** Error carrying the backend error-registry code (§2.5.1). */
export class ApiError extends Error {
  /** @param {string} code @param {string} message @param {*} [details] @param {number} [status] @param {string} [requestId] */
  constructor(code, message, details, status, requestId) {
    super(message || code);
    this.name = 'ApiError';
    this.code = code;
    this.details = details;
    this.status = status;
    this.requestId = requestId; // X-Request-ID, for support correlation
  }
}

/** @returns {string|null} */
export function getToken() {
  try {
    return localStorage.getItem(TOKEN_STORAGE_KEY);
  } catch {
    return null;
  }
}

/** @param {string|null} token */
export function setToken(token) {
  try {
    if (token) localStorage.setItem(TOKEN_STORAGE_KEY, token);
    else localStorage.removeItem(TOKEN_STORAGE_KEY);
  } catch {
    /* storage unavailable (private mode) — requests just go unauthenticated */
  }
}

/**
 * Core request. Returns the unwrapped `data` payload.
 * @param {string} path  path under the API base, e.g. '/items'
 * @param {{ method?: string, body?: *, signal?: AbortSignal, auth?: boolean, idempotencyKey?: string }} [opts]
 * @returns {Promise<*>}
 */
export async function request(path, opts = {}) {
  const { method = 'GET', body, signal, auth = true, idempotencyKey } = opts;
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;

  const token = auth ? getToken() : null;
  if (token) headers['Authorization'] = `Bearer ${token}`;

  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal,
    });
  } catch (networkErr) {
    if (networkErr && networkErr.name === 'AbortError') throw networkErr;
    throw new ApiError('NETWORK', 'Network request failed. Check your connection.');
  }

  let envelope = null;
  try {
    envelope = await response.json();
  } catch {
    envelope = null;
  }

  const requestId = response.headers.get('X-Request-ID') || undefined;
  if (envelope && typeof envelope.success === 'boolean') {
    if (envelope.success) return envelope.data;
    const err = envelope.error || {};
    throw new ApiError(err.code || 'INTERNAL', err.message || 'Request failed', err.details, response.status, requestId);
  }

  // Non-enveloped response (legacy endpoint or proxy error page).
  if (!response.ok) {
    throw new ApiError('INTERNAL', `Request failed (${response.status})`, null, response.status, requestId);
  }
  return envelope;
}

export const http = {
  get: (path, opts) => request(path, { ...opts, method: 'GET' }),
  post: (path, body, opts) => request(path, { ...opts, method: 'POST', body }),
  put: (path, body, opts) => request(path, { ...opts, method: 'PUT', body }),
  del: (path, opts) => request(path, { ...opts, method: 'DELETE' }),
};
