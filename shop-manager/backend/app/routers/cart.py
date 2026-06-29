"""Customer cart — authenticated. Absorbs customer-view's cart endpoints into
the single service; carts now reference `items` directly (one product table).
Cart lines expose only customer-safe fields (no purchase_cost)."""
import sqlite3

from fastapi import APIRouter, Depends

from app.core.envelope import success
from app.core.security import require_auth
from app.db import get_db
from app.schemas import CartAdd
from domain import compute_discount_percent

router = APIRouter(prefix="/cart", tags=["cart"], dependencies=[Depends(require_auth)])


@router.get("")
def get_cart(user: sqlite3.Row = Depends(require_auth), conn: sqlite3.Connection = Depends(get_db)):
    rows = conn.execute(
        """SELECT c.item_id, c.qty, i.name, i.price, i.mrp, i.image_url
           FROM user_cart c JOIN items i ON c.item_id = i.id
           WHERE c.user_id = ? ORDER BY c.added_at DESC""",
        (user["id"],),
    ).fetchall()
    lines = []
    for r in rows:
        line = dict(r)
        line["discount_percent"] = compute_discount_percent(line["price"], line["mrp"])
        lines.append(line)
    return success(lines)


@router.post("")
def add_to_cart(
    payload: CartAdd,
    user: sqlite3.Row = Depends(require_auth),
    conn: sqlite3.Connection = Depends(get_db),
):
    conn.execute(
        """INSERT INTO user_cart (user_id, item_id, qty) VALUES (?, ?, ?)
           ON CONFLICT(user_id, item_id) DO UPDATE SET qty = excluded.qty""",
        (user["id"], payload.item_id, payload.qty),
    )
    conn.commit()
    return success({"item_id": payload.item_id, "qty": payload.qty})


@router.delete("/{item_id}")
def remove_from_cart(
    item_id: int,
    user: sqlite3.Row = Depends(require_auth),
    conn: sqlite3.Connection = Depends(get_db),
):
    conn.execute("DELETE FROM user_cart WHERE user_id = ? AND item_id = ?", (user["id"], item_id))
    conn.commit()
    return success({"removed": item_id})
