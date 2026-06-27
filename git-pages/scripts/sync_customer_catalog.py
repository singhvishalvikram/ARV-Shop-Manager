#!/usr/bin/env python3
"""
Auto-sync: Reads Shop Manager DB + View Manager config,
filters products, and writes customer-visible data to view-manager.db.

Run this on every DB change (via server startup or cron).
"""

import sqlite3
import sys
import os
from datetime import datetime, timezone

SHOP_DB = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "shop-manager", "backend", "shop.db")
VIEW_DB = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "view-manager.db")

HIDDEN_FIELDS = {"purchase_cost", "quantity", "daily_sales", "created_at", "updated_at"}
CUSTOMER_FIELDS = ["id", "name", "type", "description", "price", "mrp", "image_url", "location"]


def dict_factory(cursor, row):
    d = {}
    for idx, col in enumerate(cursor.description):
        d[col[0]] = row[idx]
    return d


def main():
    # 1. Read settings from view manager DB
    view_conn = sqlite3.connect(VIEW_DB)
    view_conn.row_factory = dict_factory
    view_cur = view_conn.cursor()

    settings = {}
    for row in view_cur.execute("SELECT key, value FROM settings"):
        settings[row["key"]] = row["value"]

    hidden_ids = set()
    for row in view_cur.execute("SELECT product_id FROM hidden_products"):
        hidden_ids.add(row["product_id"])

    allowed_categories = []
    for row in view_cur.execute("SELECT category FROM category_filter WHERE visible = 1"):
        allowed_categories.append(row["category"])

    # View options
    show_mrp = settings.get("show_mrp", "1") == "1"
    show_description = settings.get("show_description", "1") == "1"
    show_images = settings.get("show_images", "1") == "1"
    show_location = settings.get("show_location", "1") == "1"
    max_products = int(settings.get("max_products", "0") or "0")
    allowed_cats = allowed_categories  # empty = all

    # 2. Read products from Shop Manager DB (use separate connection without dict_factory)
    shop_conn = sqlite3.connect(SHOP_DB)

    # Get columns (PRAGMA doesn't work with dict_factory)
    col_cur = shop_conn.cursor()
    col_cur.execute("PRAGMA table_info(items)")
    all_columns = {row[1] for row in col_cur.fetchall()}

    # Now set dict_factory for product queries
    shop_conn.row_factory = dict_factory
    shop_cur = shop_conn.cursor()
    available_customer = [f for f in CUSTOMER_FIELDS if f in all_columns]

    # Build SELECT clause based on view options
    select_cols = ["id", "name", "type", "price", "quantity"]
    if show_description and "description" in all_columns:
        select_cols.append("description")
    if show_mrp and "mrp" in all_columns:
        select_cols.append("mrp")
    if show_images and "image_url" in all_columns:
        select_cols.append("image_url")
    if show_location and "location" in all_columns:
        select_cols.append("location")

    query = f"SELECT {', '.join(select_cols)} FROM items"
    products = shop_cur.execute(query).fetchall()
    shop_conn.close()

    # 3. Apply filters
    filtered = []
    for p in products:
        # Skip hidden products
        if p["id"] in hidden_ids:
            continue

        # Skip non-allowed categories (if filter is set)
        if allowed_cats and p.get("type", "") not in allowed_cats:
            continue

        # Build customer product with only visible fields
        cp = {
            "id": p["id"],
            "name": p.get("name", ""),
            "type": p.get("type", ""),
            "description": p.get("description", "") if show_description else "",
            "price": p.get("price", 0),
            "mrp": p.get("mrp", 0) if show_mrp else 0,
            "image_url": p.get("image_url", "") if show_images else "",
            "location": p.get("location", "") if show_location else "",
            "discount_percent": round(((p.get("mrp", 0) - p.get("price", 0)) / p["mrp"]) * 100, 1) if show_mrp and p.get("mrp", 0) > p.get("price", 0) else 0,
            "stock_status": "out_of_stock" if p.get("quantity", 0) <= 0 else "in_stock",
        }
        filtered.append(cp)

    # Apply max limit
    if max_products > 0:
        filtered = filtered[:max_products]

    # 4. Write to view manager DB
    view_cur.execute("DELETE FROM customer_products")
    for cp in filtered:
        view_cur.execute(
            """INSERT INTO customer_products (id, name, type, description, price, mrp, image_url, location, discount_percent, synced_at, stock_status)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (cp["id"], cp["name"], cp["type"], cp["description"], cp["price"], cp["mrp"], cp["image_url"], cp["location"], cp["discount_percent"], datetime.now(timezone.utc).isoformat(), cp["stock_status"])
        )

    # 5. Log sync
    view_cur.execute(
        "INSERT INTO sync_log (products_count, synced_at) VALUES (?, ?)",
        (len(filtered), datetime.now(timezone.utc).isoformat())
    )

    view_conn.commit()
    view_conn.close()

    print(f"Synced {len(filtered)} products to view manager DB")


if __name__ == "__main__":
    main()
