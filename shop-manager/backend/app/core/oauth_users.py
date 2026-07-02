"""Federated-login user provisioning (find-or-create / account linking).

Pure DB logic with NO web-framework or Authlib import, so it is unit-testable
without a network or OAuth client. Used by the Google OIDC callback.

Standards: GUARDRAILS §2.2 (parameterized queries), §2.5 (auth).
"""
import sqlite3
from typing import Optional


def provision_oauth_user(
    conn: sqlite3.Connection,
    provider: str,
    sub: str,
    email: str,
    name: str = "",
) -> int:
    """Return the user id for a federated identity, creating or linking as needed.

    Resolution order:
      1. Existing identity (provider, sub) → return it (refresh name/email).
      2. Existing user with the same verified email → link the identity to it.
      3. Otherwise create a new owner account with an empty password hash
         (cannot password-login) and a synthetic unique `phone` (`provider:sub`)
         so the NOT-NULL/UNIQUE phone constraint holds without a real number.
    """
    if not sub:
        raise ValueError("oauth sub is required")
    email = (email or "").strip().lower()
    name = (name or "").strip()

    existing = conn.execute(
        "SELECT id FROM users WHERE auth_provider = ? AND provider_sub = ?",
        (provider, sub),
    ).fetchone()
    if existing:
        conn.execute(
            "UPDATE users SET last_login = datetime('now'),"
            " name = CASE WHEN ? != '' THEN ? ELSE name END,"
            " email = CASE WHEN ? != '' THEN ? ELSE email END"
            " WHERE id = ?",
            (name, name, email, email, existing["id"]),
        )
        conn.commit()
        return existing["id"]

    if email:
        by_email = conn.execute(
            "SELECT id FROM users WHERE email = ?", (email,)
        ).fetchone()
        if by_email:
            conn.execute(
                "UPDATE users SET auth_provider = ?, provider_sub = ?,"
                " last_login = datetime('now') WHERE id = ?",
                (provider, sub, by_email["id"]),
            )
            conn.commit()
            return by_email["id"]

    synthetic_phone = f"{provider}:{sub}"
    cursor = conn.execute(
        "INSERT INTO users (phone, name, email, password_hash, role,"
        " auth_provider, provider_sub, last_login)"
        " VALUES (?, ?, ?, '', 'owner', ?, ?, datetime('now'))",
        (synthetic_phone, name, email, provider, sub),
    )
    conn.commit()
    return cursor.lastrowid


def find_user_email(conn: sqlite3.Connection, user_id: int) -> Optional[str]:
    row = conn.execute("SELECT email FROM users WHERE id = ?", (user_id,)).fetchone()
    return row["email"] if row else None
