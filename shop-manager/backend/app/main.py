"""FastAPI application entrypoint — the single consolidated backend.

Run:  uvicorn app.main:app --host 0.0.0.0 --port 8080
Standards: CODING_STANDARDS §4.2 (envelope), §4.6 (versioning), §2.5.1
(error registry); GUARDRAILS §2.6 (no leaked exceptions), §2.8 (security headers).
"""
import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles

from app.core.config import settings
from app.core.envelope import failure
from app.core.errors import AppError, ErrorCode
from app.db import get_connection, init_schema
from app.manifest import build_owner_manifest
from app.routers import auth, cart, catalog, dashboard, health, items, sales, settings as settings_router

logger = logging.getLogger("shop_manager")

@asynccontextmanager
async def lifespan(app: FastAPI):
    init_schema()
    yield


app = FastAPI(title=settings.app_name, version=settings.app_version, lifespan=lifespan)


# CORS only when the catalog is hosted cross-origin (configured via env). The
# default is same-origin, so no cross-origin access is granted unless asked for.
if settings.cors_allow_origins:
    from fastapi.middleware.cors import CORSMiddleware

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_allow_origins,
        allow_credentials=True,
        allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],
        allow_headers=["Authorization", "Content-Type"],
    )


# ── Security headers on every response (GUARDRAILS §2.8) ──
# NOTE: 'unsafe-inline' is required while the front-ends use inline <script> and
# inline event handlers; removing inline code (nonces/external JS) is tracked as
# future hardening. wa.me links are navigations (not connect-src).
_CSP = (
    "default-src 'self'; "
    "img-src 'self' data: blob:; "
    "script-src 'self' 'unsafe-inline'; "
    "style-src 'self' 'unsafe-inline'; "
    "connect-src 'self'; "
    "base-uri 'self'; "
    "form-action 'self'; "
    "frame-ancestors 'none'"
)


@app.middleware("http")
async def security_headers(request: Request, call_next):
    response = await call_next(request)
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["X-Frame-Options"] = "DENY"
    response.headers["Referrer-Policy"] = "no-referrer"
    response.headers["Cache-Control"] = "no-store"
    response.headers["Content-Security-Policy"] = _CSP
    return response


# ── Exception handlers: every error is an envelope (never a raw trace) ──
@app.exception_handler(AppError)
async def handle_app_error(request: Request, exc: AppError):
    return JSONResponse(
        status_code=exc.status_code,
        content=failure(exc.code, exc.message, exc.details),
    )


@app.exception_handler(RequestValidationError)
async def handle_validation_error(request: Request, exc: RequestValidationError):
    return JSONResponse(
        status_code=422,
        content=jsonable_encoder(failure(ErrorCode.VALIDATION, "Request validation failed", exc.errors())),
    )


@app.exception_handler(Exception)
async def handle_unexpected(request: Request, exc: Exception):
    logger.exception("Unhandled error on %s %s", request.method, request.url.path)
    return JSONResponse(status_code=500, content=failure(ErrorCode.INTERNAL, "Internal server error"))


# ── Routers under /api/v1 ─────────────────────────────────
for module in (health, auth, items, sales, dashboard, catalog, settings_router, cart):
    app.include_router(module.router, prefix=settings.api_prefix)

# Google Sign-In is optional: its router (and the Authlib dependency) load ONLY
# when an OAuth client is configured, so the core service and tests run without it.
if settings.google_oauth_configured:
    from app.routers.auth_google import setup_google

    setup_google(app)


# ── Owner front-end (same origin as the API) ──────────────
# Served only when the static bundle is present, so the API can also run
# headless (tests, API-only deploys). The catalog stays a separate face.
if os.path.isdir(settings.frontend_static_dir):
    app.mount(
        "/static",
        StaticFiles(directory=settings.frontend_static_dir),
        name="static",
    )

    @app.get("/manifest.json", include_in_schema=False)
    async def owner_manifest():
        # Brand the installable app from settings (config-driven, ADR-002).
        conn = get_connection()
        try:
            rows = {r["key"]: r["value"] for r in conn.execute("SELECT key, value FROM settings")}
        finally:
            conn.close()
        return JSONResponse(
            build_owner_manifest(
                name=rows.get("app_title", ""),
                theme_color=rows.get("theme_color", ""),
            )
        )

    @app.get("/sw.js", include_in_schema=False)
    async def service_worker():
        # Served from the root so the SW scope is "/" (controls the whole app).
        # Service-Worker-Allowed lets the file live under /static if ever moved.
        path = os.path.join(settings.frontend_static_dir, "js", "sw.js")
        return FileResponse(
            path,
            media_type="application/javascript",
            headers={"Service-Worker-Allowed": "/"},
        )

    @app.get("/", include_in_schema=False)
    async def owner_index():
        return FileResponse(settings.owner_index_path)


# Optionally serve the customer catalog same-origin at /catalog (one backend,
# two faces). Additive — does not affect the standalone static catalog deploy.
if os.path.isdir(settings.catalog_static_dir):
    app.mount(
        "/catalog",
        StaticFiles(directory=settings.catalog_static_dir, html=True),
        name="catalog",
    )
