"""Settings administration — owner-only. Public read is via /catalog/settings
(safe subset). This is the full read/write for the shop owner."""
import sqlite3

from fastapi import APIRouter, Body, Depends

from app.core.envelope import success
from app.core.errors import AppError, ErrorCode
from app.core.security import require_auth
from app.db import get_db

router = APIRouter(prefix="/settings", tags=["settings"], dependencies=[Depends(require_auth)])


@router.get("")
def get_settings(conn: sqlite3.Connection = Depends(get_db)):
    rows = conn.execute("SELECT key, value FROM settings").fetchall()
    return success({r["key"]: r["value"] for r in rows})


@router.post("")
def update_settings(payload: dict = Body(...), conn: sqlite3.Connection = Depends(get_db)):
    if not payload:
        raise AppError(ErrorCode.VALIDATION, "No settings provided", status_code=400)
    for key, value in payload.items():
        conn.execute(
            "INSERT INTO settings (key, value) VALUES (?, ?) "
            "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            (str(key), str(value)),
        )
    conn.commit()
    return success({"updated": list(payload.keys())})
