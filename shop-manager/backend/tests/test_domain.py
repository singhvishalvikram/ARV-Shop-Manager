"""Unit tests for pure domain rules. AAA structure (CODING_STANDARDS §7.2)."""
import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from domain import (  # noqa: E402
    IN_STOCK,
    OUT_OF_STOCK,
    compute_discount_percent,
    compute_stock_status,
)


@pytest.mark.parametrize(
    "quantity, expected",
    [
        (5, IN_STOCK),
        (1, IN_STOCK),
        (0, OUT_OF_STOCK),
        (-3, OUT_OF_STOCK),
        (None, OUT_OF_STOCK),       # missing qty must not advertise stock
        ("not a number", OUT_OF_STOCK),
    ],
)
def test_compute_stock_status(quantity, expected):
    assert compute_stock_status(quantity) == expected


@pytest.mark.parametrize(
    "price, mrp, expected",
    [
        (80, 100, 20),              # normal 20% discount
        (100, 100, 0),              # no discount when equal
        (120, 100, 0),              # price above mrp -> never negative
        (100, 0, 0),                # zero mrp -> guard against div-by-zero
        (None, 100, 100),           # missing price treated as 0 -> full off mrp
        (50, None, 0),              # missing mrp -> no discount
        (33, 99, 67),               # rounds to whole number
    ],
)
def test_compute_discount_percent(price, mrp, expected):
    assert compute_discount_percent(price, mrp) == expected
