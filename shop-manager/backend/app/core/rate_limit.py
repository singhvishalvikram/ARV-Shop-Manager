"""In-memory sliding-window rate limiter for brute-force protection.

Single-instance only — the state lives in this process. At horizontal scale,
back it with a shared store (e.g. Redis); the call sites stay the same.

Standards: GUARDRAILS §2.5 (auth abuse), CODING_STANDARDS §4.1 (rate limits).
"""
import threading
import time

from app.core.errors import AppError, ErrorCode
from fastapi import Request

_lock = threading.Lock()
_hits: dict = {}  # key -> list[timestamps]


def check_rate_limit(key: str, limit: int, window_seconds: int, now: float = None) -> None:
    """Record an attempt for `key`; raise 429 AppError if it exceeds `limit`
    within the trailing `window_seconds`."""
    now = time.monotonic() if now is None else now
    cutoff = now - window_seconds
    with _lock:
        recent = [t for t in _hits.get(key, []) if t > cutoff]
        if len(recent) >= limit:
            _hits[key] = recent  # drop expired entries
            raise AppError(
                ErrorCode.RATE_LIMITED,
                "Too many attempts. Please try again later.",
                status_code=429,
            )
        recent.append(now)
        _hits[key] = recent


def client_ip(request: Request) -> str:
    """Best-effort client IP, honoring a single proxy hop via X-Forwarded-For."""
    forwarded = request.headers.get("x-forwarded-for")
    if forwarded:
        return forwarded.split(",")[0].strip()
    return request.client.host if request.client else "unknown"


def reset() -> None:
    """Clear all counters (used by tests)."""
    with _lock:
        _hits.clear()
