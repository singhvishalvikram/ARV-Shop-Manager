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
    # Brute-force protection on auth endpoints: max attempts per client IP per
    # window. In-memory (single instance); use a shared store (Redis) at scale.
    auth_rate_limit: int = int(os.environ.get("AUTH_RATE_LIMIT", "5"))
    auth_rate_window_seconds: int = int(os.environ.get("AUTH_RATE_WINDOW_SECONDS", "900"))
    # CSRF / signing secret — MUST be set in real environments. The empty
    # default is intentional so a missing secret is caught in staging, never
    # silently replaced by a hardcoded one (the bug we are removing).
    auth_secret: str = os.environ.get("AUTH_SECRET", "")

    # API
    api_prefix: str = "/api/v1"
    # White-label: no tenant brand baked in (per-shop name comes from settings).
    app_name: str = os.environ.get("APP_NAME", "Shop Manager API")
    app_version: str = "1.0.0"

    # Owner front-end served by this same service (single origin → relative
    # /api/v1, no CORS surface). Repo-relative defaults; override via env.
    frontend_static_dir: str = os.environ.get(
        "FRONTEND_STATIC_DIR", os.path.join(_BACKEND_DIR, "static")
    )
    owner_index_path: str = os.environ.get(
        "OWNER_INDEX_PATH", os.path.join(_BACKEND_DIR, "templates", "index.html")
    )

    # Item images. Stored on disk now (must live under the served static dir so
    # the URL below resolves); the storage interface is swappable for object
    # storage (S3/GCS) later without touching callers — see image_storage.py and
    # CLAUDE.md §4. Base64-in-DB is retired.
    # Cross-origin allow-list for the customer catalog when it is hosted on a
    # different origin (e.g. GitHub Pages) and calls this API. Empty = same-origin
    # only (no CORS). Comma-separated origins via env.
    cors_allow_origins: list = [
        o.strip() for o in os.environ.get("CORS_ALLOW_ORIGINS", "").split(",") if o.strip()
    ]
    # The customer catalog can also be served same-origin from this service.
    catalog_static_dir: str = os.environ.get(
        "CATALOG_STATIC_DIR",
        os.path.normpath(os.path.join(_BACKEND_DIR, "..", "..", "customer-view", "site")),
    )

    image_storage_backend: str = os.environ.get("IMAGE_STORAGE_BACKEND", "local")
    images_dir: str = os.environ.get(
        "SHOP_IMAGES_DIR", os.path.join(_BACKEND_DIR, "static", "images", "items")
    )
    image_url_prefix: str = os.environ.get("IMAGE_URL_PREFIX", "/static/images/items")
    image_max_bytes: int = int(os.environ.get("IMAGE_MAX_BYTES", str(5 * 1024 * 1024)))

    # Google Sign-In (OIDC via Authlib). Empty by default — the feature is only
    # enabled when a real OAuth client is configured (see `google_oauth_configured`).
    # Secrets live here via env only, never in source (GUARDRAILS §1.4).
    google_client_id: str = os.environ.get("GOOGLE_CLIENT_ID", "")
    google_client_secret: str = os.environ.get("GOOGLE_CLIENT_SECRET", "")
    # Absolute callback URL registered in the Google Cloud console, e.g.
    # https://app.example.com/api/v1/auth/google/callback
    google_redirect_uri: str = os.environ.get("GOOGLE_REDIRECT_URI", "")
    # Where to send the browser after a successful Google login. The session
    # token is appended in the URL fragment (#token=...) for the PWA to read.
    oauth_success_redirect: str = os.environ.get("OAUTH_SUCCESS_REDIRECT", "/")

    @property
    def google_oauth_configured(self) -> bool:
        """Google Sign-In is wired only when an OAuth client, redirect URI, and a
        signing secret (for the OAuth state cookie) are all present."""
        return bool(
            self.google_client_id
            and self.google_client_secret
            and self.google_redirect_uri
            and self.auth_secret
        )


settings = Settings()
