/**
 * Bridge: expose the ES-module service layer to the classic-script owner app
 * (`app.js`), which relies on global functions for inline handlers.
 *
 * Module scripts are deferred, so this runs after parsing but BEFORE
 * `DOMContentLoaded` — meaning `window.API` / `window.Auth` are ready before
 * `app.js`'s load handler executes. It also captures a Google-redirect token
 * (`#token=...`) the moment the page loads.
 */
import * as auth from './api/auth-service.js';
import * as items from './api/items-service.js';
import * as sales from './api/sales-service.js';
import * as dashboard from './api/dashboard-service.js';
import * as catalog from './api/catalog-service.js';
import * as cart from './api/cart-service.js';
import * as settings from './api/settings-service.js';
import { ApiError, getToken, setToken } from './api/http-client.js';

// Capture a session token arriving from the Google OIDC redirect, before any
// app logic runs.
auth.captureTokenFromFragment();

window.API = { items, sales, dashboard, catalog, cart, settings };
window.Auth = { ...auth, getToken, setToken };
window.ApiError = ApiError;
