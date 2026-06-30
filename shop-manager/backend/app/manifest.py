"""Dynamic PWA manifest for the owner app — branded per shop from settings.

One shell, branded at runtime (ADR-002): the installable app's name, theme, and
colors come from `settings`, not a baked-in file. Icons are real PNGs served
from /static/icons (192/512 + maskable).
"""

_DEFAULT_NAME = "Shop Manager"
_DEFAULT_THEME = "#2196F3"
_DEFAULT_BACKGROUND = "#ffffff"


def build_owner_manifest(
    name: str = "",
    theme_color: str = "",
    background_color: str = "",
) -> dict:
    """Return a web app manifest dict, falling back to generic defaults."""
    name = name or _DEFAULT_NAME
    theme = theme_color or _DEFAULT_THEME
    background = background_color or _DEFAULT_BACKGROUND
    return {
        "name": name,
        "short_name": name[:12],
        "description": "Inventory and sales manager",
        "start_url": "/",
        "scope": "/",
        "display": "standalone",
        "orientation": "portrait-primary",
        "background_color": background,
        "theme_color": theme,
        "icons": [
            {"src": "/static/icons/icon-192.png", "sizes": "192x192",
             "type": "image/png", "purpose": "any"},
            {"src": "/static/icons/icon-512.png", "sizes": "512x512",
             "type": "image/png", "purpose": "any"},
            {"src": "/static/icons/icon-512.png", "sizes": "512x512",
             "type": "image/png", "purpose": "maskable"},
        ],
        "categories": ["business", "productivity"],
    }
