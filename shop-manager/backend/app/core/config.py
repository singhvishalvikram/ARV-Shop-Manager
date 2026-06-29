"""Application configuration — env-driven, no hardcoded host paths.

Standards: GUARDRAILS §1.4 (Environment & Secret Management).
Reuses the same SHOP_DB_PATH as the legacy Flask app so both point at one DB
during the fold-in transition.
"""
import os

_BACKEND_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


class Settings:
    # Database (shared with legacy app.py via SHOP_DB_PATH)
    db_path: str = os.environ.get("SHOP_DB_PATH", os.path.join(_BACKEND_DIR, "shop.db"))

    # Sessions / auth
    session_ttl_days: int = int(os.environ.get("SESSION_TTL_DAYS", "30"))
    # CSRF / signing secret — MUST be set in real environments. The empty
    # default is intentional so a missing secret is caught in staging, never
    # silently replaced by a hardcoded one (the bug we are removing).
    auth_secret: str = os.environ.get("AUTH_SECRET", "")

    # API
    api_prefix: str = "/api/v1"
    app_name: str = "ARV Shop Manager API"
    app_version: str = "1.0.0"


settings = Settings()
