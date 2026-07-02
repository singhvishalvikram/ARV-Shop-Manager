"""Public customer catalog — the second "face" of the single app.

SECURITY: this is unauthenticated and customer-facing, so it exposes ONLY
safe merchandising fields. purchase_cost, location, and raw quantity are
owner-only and MUST NOT appear here (GUARDRAILS §2.4, §1.2 least privilege).
This replaces customer-view's generate.py / products.json pipeline by reading
the one database directly.
"""
import sqlite3

from fastapi import APIRouter, Depends

from app.core.envelope import success
from app.db import get_db
from domain import compute_discount_percent, compute_stock_status

router = APIRouter(prefix="/catalog", tags=["catalog"])

# Public, customer-safe fields only. Cost/location/quantity deliberately absent.
_PUBLIC_SETTINGS = {
    "app_title", "app_subtitle", "whatsapp_number", "shop_location",
    "currency_symbol", "theme_color", "show_search", "show_category_filter",
    "show_discount_badges", "show_mrp",
}


def _to_public_product(row: sqlite3.Row) -> dict:
    item = dict(row)
    name = item["title_override"] or item["name"]
    description = item["description_override"] or item["description"]
    return {
        "id": item["id"],
        "name": name,
        "type": item["type"],
        "description": description,
        "price": item["price"],
        "mrp": item["mrp"],
        "discount_percent": compute_discount_percent(item["price"], item["mrp"]),
        "image_url": item["image_url"],
        "stock_status": compute_stock_status(item["quantity"]),
        "featured": bool(item["featured"]),
        "badge": item["badge"],
    }


@router.get("/products")
def list_public_products(conn: sqlite3.Connection = Depends(get_db)):
    rows = conn.execute(
        """SELECT id, name, type, description, price, mrp, image_url, quantity,
                  featured, badge, title_override, description_override
           FROM items WHERE visible = 1
           ORDER BY sort_order ASC, id ASC"""
    ).fetchall()
    return success([_to_public_product(r) for r in rows])


@router.get("/categories")
def list_public_categories(conn: sqlite3.Connection = Depends(get_db)):
    rows = conn.execute(
        "SELECT DISTINCT type FROM items WHERE visible = 1 AND type != '' ORDER BY type ASC"
    ).fetchall()
    return success([r["type"] for r in rows])


@router.get("/settings")
def public_settings(conn: sqlite3.Connection = Depends(get_db)):
    rows = conn.execute("SELECT key, value FROM settings").fetchall()
    return success({r["key"]: r["value"] for r in rows if r["key"] in _PUBLIC_SETTINGS})
