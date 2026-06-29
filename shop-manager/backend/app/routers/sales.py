"""Sales routes — owner-only. Recording a sale atomically decrements stock
and logs the sale (transaction boundary — CODING_STANDARDS §2.6)."""
import sqlite3

from fastapi import APIRouter, Depends

from app.core.envelope import success
from app.core.errors import AppError, ErrorCode
from app.core.security import require_auth
from app.db import get_db
from app.schemas import SaleCreate

router = APIRouter(prefix="/sales", tags=["sales"], dependencies=[Depends(require_auth)])


@router.post("", status_code=201)
def create_sale(payload: SaleCreate, conn: sqlite3.Connection = Depends(get_db)):
    item = conn.execute("SELECT quantity FROM items WHERE id = ?", (payload.item_id,)).fetchone()
    if item is None:
        raise AppError(ErrorCode.NOT_FOUND, "Item not found", status_code=404)
    if payload.quantity > item["quantity"]:
        raise AppError(
            ErrorCode.INSUFFICIENT_STOCK,
            "Not enough stock",
            status_code=409,
            details={"available": item["quantity"], "requested": payload.quantity},
        )
    try:
        conn.execute("BEGIN")
        conn.execute(
            "UPDATE items SET quantity = quantity - ?, updated_at = datetime('now') WHERE id = ?",
            (payload.quantity, payload.item_id),
        )
        conn.execute(
            """INSERT INTO daily_sales (item_id, quantity_sold, sale_price, description)
               VALUES (?, ?, ?, ?)""",
            (payload.item_id, payload.quantity, payload.price, payload.description.strip()),
        )
        conn.commit()
    except sqlite3.Error:
        conn.rollback()
        raise AppError(ErrorCode.INTERNAL, "Failed to record sale", status_code=500)
    return success({"recorded": True})


@router.get("")
def list_sales(date: str = "", conn: sqlite3.Connection = Depends(get_db)):
    date = date.strip()
    if date:
        rows = conn.execute(
            """SELECT s.*, i.name AS item_name, i.type AS item_type
               FROM daily_sales s JOIN items i ON s.item_id = i.id
               WHERE DATE(s.sale_date) = ? ORDER BY s.sale_date DESC""",
            (date,),
        ).fetchall()
    else:
        rows = conn.execute(
            """SELECT s.*, i.name AS item_name, i.type AS item_type
               FROM daily_sales s JOIN items i ON s.item_id = i.id
               ORDER BY s.sale_date DESC LIMIT 100"""
        ).fetchall()
    total = conn.execute(
        "SELECT COALESCE(SUM(quantity_sold * sale_price), 0) AS r FROM daily_sales"
    ).fetchone()["r"]
    return success({"sales": [dict(r) for r in rows], "total_revenue": round(total, 2), "count": len(rows)})
