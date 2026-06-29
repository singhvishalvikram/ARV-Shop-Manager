"""FastAPI application entrypoint — the single consolidated backend.

Run:  uvicorn app.main:app --host 0.0.0.0 --port 8080
Standards: CODING_STANDARDS §4.2 (envelope), §4.6 (versioning), §2.5.1
(error registry); GUARDRAILS §2.6 (no leaked exceptions), §2.8 (security headers).
"""
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.core.config import settings
from app.core.envelope import failure
from app.core.errors import AppError, ErrorCode
from app.db import init_schema
from app.routers import auth, dashboard, health, items, sales

logger = logging.getLogger("shop_manager")

@asynccontextmanager
async def lifespan(app: FastAPI):
    init_schema()
    yield


app = FastAPI(title=settings.app_name, version=settings.app_version, lifespan=lifespan)


# ── Security headers on every response (GUARDRAILS §2.8) ──
@app.middleware("http")
async def security_headers(request: Request, call_next):
    response = await call_next(request)
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["X-Frame-Options"] = "DENY"
    response.headers["Referrer-Policy"] = "no-referrer"
    response.headers["Cache-Control"] = "no-store"
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
for module in (health, auth, items, sales, dashboard):
    app.include_router(module.router, prefix=settings.api_prefix)
