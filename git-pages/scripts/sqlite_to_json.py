import sqlite3
import json
import os

DB_FILE = "shop-manager-live.db"
OUTPUT_FILE = "../data/products.json"

conn = sqlite3.connect(DB_FILE)
cursor = conn.cursor()

cursor.execute("""
SELECT
    id,
    name,
    type,
    description,
    price,
    mrp,
    image_url
FROM items
""")

products = []

for row in cursor.fetchall():
    (
        product_id,
        name,
        product_type,
        description,
        price,
        mrp,
        image_url
    ) = row

    try:
        discount = round(((mrp - price) / mrp) * 100) if mrp and mrp > price else 0
    except:
        discount = 0

    products.append({
        "id": product_id,
        "name": name,
        "type": product_type,
        "description": description or "",
        "price": price,
        "mrp": mrp,
        "discount_percent": discount,
        "image_url": image_url or ""
    })

conn.close()

os.makedirs("../data", exist_ok=True)

with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
    json.dump(products, f, ensure_ascii=False, indent=2)

print(f"Exported {len(products)} products")
