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
            """
        )
        conn.commit()
    finally:
        conn.close()
