"""
Pure domain logic — no framework, no I/O, no DB.

This is the single source of truth for business rules that are currently
duplicated across app.py (Flask) and customer-view/scripts/import.py.
Keeping them here means the future FastAPI service and the import pipeline
compute identical values. Standards: CODING_STANDARDS §1.10 (Shared Source
of Truth), §2.4 (Single Responsibility).

Every function here MUST stay pure (deterministic, side-effect free) so it
is trivially unit-testable without spinning up a server or a database.
"""
from __future__ import annotations

IN_STOCK = "in_stock"
OUT_OF_STOCK = "out_of_stock"


def compute_stock_status(quantity: int | float | None) -> str:
    """Return stock status from on-hand quantity.

    A non-positive, missing, or non-numeric quantity is treated as out of
    stock — the safe default, so a bad value never advertises availability.
    """
    try:
        qty = float(quantity) if quantity is not None else 0.0
    except (TypeError, ValueError):
        return OUT_OF_STOCK
    return OUT_OF_STOCK if qty <= 0 else IN_STOCK


def compute_discount_percent(price: float | None, mrp: float | None) -> int:
    """Whole-number discount % of selling `price` against `mrp`.

    Returns 0 unless mrp is a positive number strictly greater than price,
    which prevents negative or nonsensical discounts from bad data.
    """
    try:
        price_value = float(price) if price is not None else 0.0
        mrp_value = float(mrp) if mrp is not None else 0.0
    except (TypeError, ValueError):
        return 0
    if mrp_value > 0 and mrp_value > price_value:
        return round(((mrp_value - price_value) / mrp_value) * 100)
    return 0
