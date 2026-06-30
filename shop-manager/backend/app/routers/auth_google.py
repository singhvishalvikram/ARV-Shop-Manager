"""Google Sign-In (OIDC) via Authlib.

This module imports Authlib at load time, so it is imported by `main.py` ONLY
when `settings.google_oauth_configured` is true. That keeps Authlib an optional
dependency and lets the test suite run without it or a network.

Flow: /auth/google/login redirects to Google; /auth/google/callback exchanges
the code, provisions/links the user, mints our own server-side session token,
and redirects the browser to the app with `#token=<session>` in the fragment.

Standards: GUARDRAILS §2.5 (auth/session), §1.4 (secrets via env).
"""
import sqlite3

from authlib.integrations.starlette_client import OAuth, OAuthError
from fastapi import APIRouter, Depends, FastAPI, Request
from fastapi.responses import RedirectResponse
from starlette.middleware.sessions import SessionMiddleware

from app.core.config import settings
from app.core.oauth_users import provision_oauth_user
from app.core.security import create_session
from app.db import get_db

_GOOGLE_METADATA_URL = "https://accounts.google.com/.well-known/openid-configuration"


def setup_google(app: FastAPI) -> None:
    """Register the Google OAuth client, the OAuth state cookie middleware, and
    the login/callback routes. Called once at startup when configured."""
    oauth = OAuth()
    oauth.register(
        name="google",
        client_id=settings.google_client_id,
        client_secret=settings.google_client_secret,
        server_metadata_url=_GOOGLE_METADATA_URL,
        client_kwargs={"scope": "openid email profile"},
    )

    # Authlib stores the OAuth state/nonce in the signed session cookie. Scope
    # the cookie to the callback path and require HTTPS.
    app.add_middleware(
        SessionMiddleware,
        secret_key=settings.auth_secret,
        same_site="lax",
        https_only=True,
        max_age=600,
    )

    router = APIRouter(prefix="/auth/google", tags=["auth"])

    @router.get("/login")
    async def google_login(request: Request):
        return await oauth.google.authorize_redirect(request, settings.google_redirect_uri)

    @router.get("/callback")
    async def google_callback(request: Request, conn: sqlite3.Connection = Depends(get_db)):
        try:
            token = await oauth.google.authorize_access_token(request)
        except OAuthError:
            return RedirectResponse(_failure_url())
        userinfo = token.get("userinfo") or {}
        sub = userinfo.get("sub")
        if not sub:
            return RedirectResponse(_failure_url())
        user_id = provision_oauth_user(
            conn,
            provider="google",
            sub=sub,
            email=userinfo.get("email", ""),
            name=userinfo.get("name", ""),
        )
        session_token = create_session(conn, user_id)
        return RedirectResponse(_success_url(session_token))

    app.include_router(router, prefix=settings.api_prefix)


def _success_url(session_token: str) -> str:
    base = settings.oauth_success_redirect or "/"
    sep = "&" if "#" in base else "#"
    return f"{base}{sep}token={session_token}"


def _failure_url() -> str:
    base = settings.oauth_success_redirect or "/"
    sep = "&" if "#" in base else "#"
    return f"{base}{sep}auth_error=google"
