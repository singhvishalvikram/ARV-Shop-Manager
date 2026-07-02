"""Authentication & password security.

- Passwords hashed with **argon2id** (per-call random salt) — replaces the
  legacy unsalted SHA-256 + hardcoded secret in server.js.
- Sessions are opaque random tokens stored server-side with an expiry.

Standards: GUARDRAILS §2.5 (Secure Session & Authentication Management).
"""
import secrets
import sqlite3
from datetime import datetime, timedelta, timezone
from typing import Optional

from argon2 import PasswordHasher
from argon2.exceptions import Argon2Error
from fastapi import Depends, Header

from app.core.config import settings
from app.core.errors import AppError, ErrorCode
from app.db import get_db

_hasher = PasswordHasher()


def hash_password(plain: str) -> str:
    return _hasher.hash(plain)


def verify_password(stored_hash: str, plain: str) -> bool:
    """True iff `plain` matches. Never raises on a bad password."""
    try:
        return _hasher.verify(stored_hash, plain)
    except (Argon2Error, Exception):
        return False


def create_session(conn: sqlite3.Connection, user_id: int) -> str:
    token = secrets.token_urlsafe(32)
    expires = (datetime.now(timezone.utc) + timedelta(days=settings.session_ttl_days)).isoformat()
    conn.execute(
        "INSERT INTO sessions (user_id, token, expires) VALUES (?, ?, ?)",
        (user_id, token, expires),
    )
    conn.commit()
    return token


def revoke_session(conn: sqlite3.Connection, token: str) -> None:
    conn.execute("DELETE FROM sessions WHERE token = ?", (token,))
    conn.commit()


def _extract_bearer(authorization: Optional[str]) -> Optional[str]:
    if not authorization:
        return None
    parts = authorization.split(" ", 1)
    if len(parts) == 2 and parts[0].lower() == "bearer":
        return parts[1].strip()
    return None


def require_auth(
    authorization: Optional[str] = Header(default=None),
    conn: sqlite3.Connection = Depends(get_db),
) -> sqlite3.Row:
    """FastAPI dependency: rejects any request without a valid, unexpired
    session token. This is the server-side gate the legacy Flask API never
    had (its 'PIN' was localStorage only)."""
    token = _extract_bearer(authorization)
    if not token:
        raise AppError(ErrorCode.UNAUTHORIZED, "Authentication required", status_code=401)
    row = conn.execute(
        """SELECT u.id, u.phone, u.name, u.role, u.email, u.auth_provider
           FROM sessions s JOIN users u ON s.user_id = u.id
           WHERE s.token = ? AND s.expires > ?""",
        (token, datetime.now(timezone.utc).isoformat()),
    ).fetchone()
    if row is None:
        raise AppError(ErrorCode.UNAUTHORIZED, "Invalid or expired session", status_code=401)
    return row
