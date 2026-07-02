"""SQLite access layer — parameterized queries only.

Replaces the legacy Node "write a Python file to /tmp and shell out per
query" anti-pattern (server.js) entirely. Standards: GUARDRAILS §2.2
(Injection Prevention), CODING_STANDARDS §4.4 (DB Efficiency).
"""
import sqlite3
from collections.abc import Iterator

from app.core.config import settings


def get_connection() -> sqlite3.Connection:
    conn = sqlite3.connect(settings.db_path)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA foreign_keys=ON")
    return conn


def get_db() -> Iterator[sqlite3.Connection]:
    """FastAPI dependency: one connection per request, always closed."""
    conn = get_connection()
    try:
        yield conn
    finally:
        conn.close()


def init_schema() -> None:
    """Create tables if absent. Additive only — safe to run on the existing
    shop.db. `items`/`daily_sales` match the legacy schema; `users`/`sessions`
    are added so auth lives server-side (replacing client-side PIN +
    SHA-256)."""
    conn = get_connection()
    try:
        conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                type TEXT NOT NULL DEFAULT '',
                description TEXT NOT NULL DEFAULT '',
                price REAL NOT NULL DEFAULT 0.0,
                mrp REAL NOT NULL DEFAULT 0.0,
                purchase_cost REAL NOT NULL DEFAULT 0.0,
                image_url TEXT NOT NULL DEFAULT '',
                location TEXT NOT NULL DEFAULT '',
                quantity INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                updated_at TEXT NOT NULL DEFAULT (datetime('now'))
            );
            CREATE TABLE IF NOT EXISTS daily_sales (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                item_id INTEGER NOT NULL,
                quantity_sold INTEGER NOT NULL,
                sale_price REAL NOT NULL,
                sale_date TEXT NOT NULL DEFAULT (datetime('now')),
                description TEXT NOT NULL DEFAULT '',
                FOREIGN KEY (item_id) REFERENCES items (id)
            );
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                phone TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL DEFAULT '',
                password_hash TEXT NOT NULL,
                role TEXT NOT NULL DEFAULT 'owner',
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                last_login TEXT
            );
            CREATE TABLE IF NOT EXISTS sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                token TEXT NOT NULL UNIQUE,
                expires TEXT NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
            );
            CREATE INDEX IF NOT EXISTS idx_sessions_token ON sessions(token);
            CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);

            -- App-wide key/value settings (absorbs customer-view.db settings).
            CREATE TABLE IF NOT EXISTS settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL DEFAULT ''
            );

            -- Customer carts (absorbs customer-view.db user_cart). References
            -- items directly now that there is one product table.
            CREATE TABLE IF NOT EXISTS user_cart (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                item_id INTEGER NOT NULL,
                qty INTEGER NOT NULL DEFAULT 1,
                added_at TEXT NOT NULL DEFAULT (datetime('now')),
                UNIQUE(user_id, item_id),
                FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
                FOREIGN KEY (item_id) REFERENCES items (id) ON DELETE CASCADE
            );
            CREATE INDEX IF NOT EXISTS idx_cart_user ON user_cart(user_id);

            -- Idempotency keys so a retried/replayed create (e.g. an offline
            -- queue flush) does not double-insert. Maps a client key to the
            -- row it created. (GUARDRAILS race-condition / §4.9 idempotency.)
            CREATE TABLE IF NOT EXISTS idempotency_keys (
                key TEXT PRIMARY KEY,
                resource TEXT NOT NULL,
                resource_id INTEGER NOT NULL,
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            );
            """
        )

        # Merchandising fields move ONTO items (was the separate
        # customer-view.db `products` table). Additive ALTERs, idempotent.
        _ensure_column(conn, "items", "visible", "INTEGER NOT NULL DEFAULT 1")
        _ensure_column(conn, "items", "featured", "INTEGER NOT NULL DEFAULT 0")
        _ensure_column(conn, "items", "badge", "TEXT NOT NULL DEFAULT ''")
        _ensure_column(conn, "items", "sort_order", "INTEGER NOT NULL DEFAULT 0")
        _ensure_column(conn, "items", "title_override", "TEXT NOT NULL DEFAULT ''")
        _ensure_column(conn, "items", "description_override", "TEXT NOT NULL DEFAULT ''")

        # Federated-login support (Google OIDC). Additive, idempotent (expand/
        # contract, GUARDRAILS §6.1). `phone`/`password_hash` stay for password
        # users; OAuth users carry an empty password_hash (cannot password-login)
        # and identify by (auth_provider, provider_sub). `email` enables linking
        # a Google account to an existing record.
        _ensure_column(conn, "users", "email", "TEXT NOT NULL DEFAULT ''")
        _ensure_column(conn, "users", "auth_provider", "TEXT NOT NULL DEFAULT 'password'")
        _ensure_column(conn, "users", "provider_sub", "TEXT NOT NULL DEFAULT ''")
        # Partial unique indexes so blanks (password users) don't collide.
        conn.execute(
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email "
            "ON users(email) WHERE email != ''"
        )
        conn.execute(
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_users_provider "
            "ON users(auth_provider, provider_sub) WHERE provider_sub != ''"
        )

        _seed_default_settings(conn)
        conn.commit()
    finally:
        conn.close()


def _ensure_column(conn: sqlite3.Connection, table: str, column: str, decl: str) -> None:
    """Add a column only if absent — SQLite has no ADD COLUMN IF NOT EXISTS.
    `table`/`column`/`decl` are developer constants, never user input."""
    existing = {row["name"] for row in conn.execute(f"PRAGMA table_info({table})")}
    if column not in existing:
        conn.execute(f"ALTER TABLE {table} ADD COLUMN {column} {decl}")


# Generic, white-label-safe defaults (no "ARV" branding — CLAUDE.md §4 Phase 5).
_DEFAULT_SETTINGS = {
    "app_title": "",
    "app_subtitle": "Product Catalog",
    "whatsapp_number": "",
    "shop_location": "",
    "currency_symbol": "₹",
    "theme_color": "#6366f1",
    "show_search": "1",
    "show_category_filter": "1",
    "show_discount_badges": "1",
    "show_mrp": "1",
}


def _seed_default_settings(conn: sqlite3.Connection) -> None:
    for key, value in _DEFAULT_SETTINGS.items():
        conn.execute("INSERT OR IGNORE INTO settings (key, value) VALUES (?, ?)", (key, value))
