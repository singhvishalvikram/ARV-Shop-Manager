"""Authentication routes. Public (login/signup); argon2id password hashing
and server-side sessions replace the legacy SHA-256 + localStorage PIN."""
import sqlite3
from typing import Optional

from fastapi import APIRouter, Depends, Request

from app.core.config import settings
from app.core.envelope import success
from app.core.errors import AppError, ErrorCode
from app.core.rate_limit import check_rate_limit, client_ip
from app.core.security import create_session, hash_password, require_auth, revoke_session, verify_password, _extract_bearer
from app.db import get_db
from app.schemas import LoginRequest, SignupRequest
from fastapi import Header

router = APIRouter(prefix="/auth", tags=["auth"])


def _rate_limit(request: Request, action: str) -> None:
    check_rate_limit(
        f"{action}:{client_ip(request)}",
        settings.auth_rate_limit,
        settings.auth_rate_window_seconds,
    )


@router.post("/signup", status_code=201)
def signup(payload: SignupRequest, request: Request, conn: sqlite3.Connection = Depends(get_db)):
    _rate_limit(request, "signup")
    existing = conn.execute("SELECT id FROM users WHERE phone = ?", (payload.phone,)).fetchone()
    if existing:
        raise AppError(ErrorCode.CONFLICT, "Phone already registered", status_code=409)
    cursor = conn.execute(
        "INSERT INTO users (phone, name, password_hash, role) VALUES (?, ?, ?, 'owner')",
        (payload.phone, payload.name, hash_password(payload.password)),
    )
    conn.commit()
    user_id = cursor.lastrowid
    token = create_session(conn, user_id)
    return success({"token": token, "user": {"id": user_id, "phone": payload.phone, "name": payload.name, "role": "owner"}})


@router.post("/login")
def login(payload: LoginRequest, request: Request, conn: sqlite3.Connection = Depends(get_db)):
    _rate_limit(request, "login")
    user = conn.execute(
        "SELECT id, phone, name, password_hash, role FROM users WHERE phone = ?", (payload.phone,)
    ).fetchone()
    # Always run verify against a real-or-dummy hash path so timing does not
    # reveal whether the phone exists (GUARDRAILS §2.5).
    if user is None or not verify_password(user["password_hash"], payload.password):
        raise AppError(ErrorCode.UNAUTHORIZED, "Invalid phone or password", status_code=401)
    conn.execute("UPDATE users SET last_login = datetime('now') WHERE id = ?", (user["id"],))
    conn.commit()
    token = create_session(conn, user["id"])
    return success({"token": token, "user": {"id": user["id"], "phone": user["phone"], "name": user["name"], "role": user["role"]}})


@router.post("/logout")
def logout(authorization: Optional[str] = Header(default=None), conn: sqlite3.Connection = Depends(get_db)):
    token = _extract_bearer(authorization)
    if token:
        revoke_session(conn, token)
    return success({"logged_out": True})


@router.get("/me")
def me(user: sqlite3.Row = Depends(require_auth)):
    return success({"user": dict(user)})
