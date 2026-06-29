"""Inventory item routes. Owner-only — items carry purchase_cost, which the
public catalog must never expose, so every route requires auth."""
import sqlite3
from datetime import datetime, timezone

from fastapi import APIRouter, Depends

from app.core.envelope import success
from app.core.errors import AppError, ErrorCode
from app.core.security import require_auth
from app.db import get_db
from app.schemas import ItemCreate, ItemUpdate
from domain import compute_stock_status

router = APIRouter(prefix="/items", tags=["items"], dependencies=[Depends(require_auth)])


def _row_to_item(row: sqlite3.Row) -> dict:
    item = dict(row)
    item["stock_status"] = compute_stock_status(item.get("quantity"))
    return item


@router.get("")
def list_items(search: str = "", conn: sqlite3.Connection = Depends(get_db)):
    search = search.strip()
    if search:
        like = f"%{search}%"
        rows = conn.execute(
            """SELECT * FROM items
               WHERE name LIKE ? OR type LIKE ? OR description LIKE ?
               ORDER BY updated_at DESC""",
            (like, like, like),
        ).fetchall()
    else:
        rows = conn.execute("SELECT * FROM items ORDER BY updated_at DESC").fetchall()
    return success([_row_to_item(r) for r in rows])


@router.get("/{item_id}")
def get_item(item_id: int, conn: sqlite3.Connection = Depends(get_db)):
    row = conn.execute("SELECT * FROM items WHERE id = ?", (item_id,)).fetchone()
    if row is None:
        raise AppError(ErrorCode.NOT_FOUND, "Item not found", status_code=404)
    return success(_row_to_item(row))


@router.post("", status_code=201)
def create_item(payload: ItemCreate, conn: sqlite3.Connection = Depends(get_db)):
    mrp = payload.mrp if payload.mrp is not None else round(payload.price * 1.2, 2)
    purchase_cost = (
        payload.purchase_cost if payload.purchase_cost is not None else round(payload.price * 0.8, 2)
    )
    # NOTE: image_base64 is intentionally not persisted yet. Base64-in-DB is
    # being retired in favour of object storage (see CLAUDE.md §4); wiring that
    # is a follow-up ticket, not part of this auth/consolidation slice.
    cursor = conn.execute(
        """INSERT INTO items (name, type, description, price, mrp, purchase_cost, location, quantity)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
        (payload.name.strip(), payload.type.strip(), payload.description.strip(),
         payload.price, mrp, purchase_cost, payload.location.strip(), payload.quantity),
    )
    conn.commit()
    row = conn.execute("SELECT * FROM items WHERE id = ?", (cursor.lastrowid,)).fetchone()
    return success(_row_to_item(row))


@router.put("/{item_id}")
def update_item(item_id: int, payload: ItemUpdate, conn: sqlite3.Connection = Depends(get_db)):
    row = conn.execute("SELECT * FROM items WHERE id = ?", (item_id,)).fetchone()
    if row is None:
        raise AppError(ErrorCode.NOT_FOUND, "Item not found", status_code=404)

    updatable = ("name", "type", "description", "price", "mrp", "purchase_cost", "location", "quantity")
    fields = {k: v for k, v in payload.model_dump(exclude_unset=True).items() if k in updatable}
    if not fields:
        raise AppError(ErrorCode.VALIDATION, "No updatable fields provided", status_code=400)

    fields["updated_at"] = datetime.now(timezone.utc).isoformat()
    set_clause = ", ".join(f"{k} = ?" for k in fields)
    conn.execute(f"UPDATE items SET {set_clause} WHERE id = ?", [*fields.values(), item_id])
    conn.commit()
    row = conn.execute("SELECT * FROM items WHERE id = ?", (item_id,)).fetchone()
    return success(_row_to_item(row))


@router.delete("/{item_id}")
def delete_item(item_id: int, conn: sqlite3.Connection = Depends(get_db)):
    row = conn.execute("SELECT id FROM items WHERE id = ?", (item_id,)).fetchone()
    if row is None:
        raise AppError(ErrorCode.NOT_FOUND, "Item not found", status_code=404)
    conn.execute("DELETE FROM items WHERE id = ?", (item_id,))
    conn.commit()
    return success({"deleted": item_id})
