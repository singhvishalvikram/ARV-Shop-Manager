"""Dashboard stats — owner-only aggregate view."""
import sqlite3

from fastapi import APIRouter, Depends

from app.core.envelope import success
from app.core.security import require_auth
from app.db import get_db

router = APIRouter(prefix="/dashboard", tags=["dashboard"], dependencies=[Depends(require_auth)])


@router.get("")
def dashboard(conn: sqlite3.Connection = Depends(get_db)):
    total_items = conn.execute("SELECT COUNT(*) AS c FROM items").fetchone()["c"]
    total_quantity = conn.execute("SELECT COALESCE(SUM(quantity), 0) AS q FROM items").fetchone()["q"]
    stock_value = conn.execute(
        "SELECT COALESCE(SUM(price * quantity), 0) AS v FROM items"
    ).fetchone()["v"]
    stock_cost = conn.execute(
        "SELECT COALESCE(SUM(purchase_cost * quantity), 0) AS v FROM items"
    ).fetchone()["v"]
    today_revenue = conn.execute(
        """SELECT COALESCE(SUM(quantity_sold * sale_price), 0) AS r
           FROM daily_sales WHERE DATE(sale_date) = DATE('now')"""
    ).fetchone()["r"]
    type_breakdown = [
        dict(r)
        for r in conn.execute(
            "SELECT type, COUNT(*) AS count FROM items WHERE type != '' GROUP BY type ORDER BY count DESC"
        ).fetchall()
    ]
    return success({
        "total_items": total_items,
        "total_quantity": total_quantity,
        "total_stock_value": round(stock_value, 2),
        "total_stock_cost": round(stock_cost, 2),
        "today_revenue": round(today_revenue, 2),
        "type_breakdown": type_breakdown,
    })
