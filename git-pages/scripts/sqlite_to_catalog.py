#!/usr/bin/env python3
"""
Cinema123BW Customer Catalog Generator

Reads the Shop Manager SQLite database and generates safe customer catalog files.
Only exposes customer-visible fields. Never exposes purchase_cost, location, quantity, etc.
"""

import sqlite3
import json
import sys
import os
from datetime import datetime, timezone

# Configuration
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
DATA_DIR = os.path.join(PROJECT_ROOT, "data")

REQUIRED_FIELDS = {"id", "name", "type", "description", "price", "mrp", "image_url"}
CUSTOMER_FIELDS = ["id", "name", "type", "description", "price", "mrp", "image_url", "location"]
HIDDEN_FIELDS = {"purchase_cost", "quantity", "daily_sales", "created_at", "updated_at"}

# Source static folder for path-based images (used by server and GitHub Pages)
SHOP_STATIC_DIR = os.path.join(PROJECT_ROOT, "shop-manager", "backend", "static", "images", "items")
# Local images directory for GitHub Pages export
LOCAL_IMAGES_DIR = os.path.join(PROJECT_ROOT, "public", "images")


def validate_schema(cursor) -> bool:
    """Validate that the database has the required schema."""
    cursor.execute("PRAGMA table_info(items)")
    columns = {row[1] for row in cursor.fetchall()}
    
    missing = REQUIRED_FIELDS - columns
    if missing:
        print(f"ERROR: Missing required fields: {missing}", file=sys.stderr)
        return False
    
    # Check for hidden fields and warn
    found_hidden = HIDDEN_FIELDS & columns
    if found_hidden:
        print(f"OK: Hidden fields found (will be excluded): {found_hidden}")
    
    print(f"OK: Schema validated. Columns: {sorted(columns)}")
    return True


def calculate_discount(price: float, mrp: float) -> int:
    """Calculate discount percentage."""
    if mrp > price and mrp > 0:
        return round(((mrp - price) / mrp) * 100)
    return 0


def extract_base64_image(image_url, product_id, images_dir):
    """If image_url is base64, save it as a file and return the path."""
    if not image_url or not image_url.startswith("data:"):
        return image_url
    try:
        import base64
        # Parse: data:image/jpeg;base64,/9j/...
        header, data = image_url.split(",", 1)
        ext_match = {"image/jpeg": ".jpg", "image/png": ".png", "image/webp": ".webp", "image/gif": ".gif"}
        mime = header.split(":")[1].split(";")[0]
        ext = ext_match.get(mime, ".jpg")
        filename = f"item_{product_id}{ext}"
        filepath = os.path.join(images_dir, filename)
        os.makedirs(images_dir, exist_ok=True)
        with open(filepath, "wb") as f:
            f.write(base64.b64decode(data))
        return f"/static/images/items/{filename}"
    except Exception as e:
        print(f"Warning: Failed to extract base64 image for product {product_id}: {e}")
        return ""


def extract_products(cursor) -> list:
    """Extract customer-safe product data. Base64 images are saved as files."""
    cursor.execute("""
        SELECT id, name, type, description, price, mrp, image_url, quantity
        FROM items 
        ORDER BY name ASC
    """)
    
    products = []
    for row in cursor.fetchall():
        img_url = row[6] if row[6] else ""
        # Convert base64 to file if needed
        if img_url.startswith("data:"):
            img_url = extract_base64_image(img_url, row[0], LOCAL_IMAGES_DIR)
        
        stock_status = "out_of_stock" if (row[7] or 0) <= 0 else "in_stock"
        
        product = {
            "id": row[0],
            "name": row[1],
            "type": row[2] if row[2] else "Uncategorized",
            "description": row[3] if row[3] else "",
            "price": row[4] if row[4] else 0,
            "mrp": row[5] if row[5] else 0,
            "image_url": img_url,
            "discount_percent": calculate_discount(row[4] or 0, row[5] or 0),
            "stock_status": stock_status
        }
        products.append(product)
    
    return products


def extract_categories(products: list) -> list:
    """Extract unique categories from products."""
    categories = sorted(set(p["type"] for p in products if p["type"]))
    return categories


def generate_version() -> dict:
    """Generate version info."""
    return {
        "version": int(datetime.now(timezone.utc).timestamp()),
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "product_count": 0,
        "category_count": 0
    }


def validate_database(db_path: str) -> tuple:
    """Validate the database file before processing."""
    if not os.path.exists(db_path):
        return False, f"Database file not found: {db_path}"
    
    if not db_path.endswith('.db'):
        return False, "File must be a .db file"
    
    try:
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()
        
        # Check if items table exists
        cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='items'")
        if not cursor.fetchone():
            conn.close()
            return False, "No 'items' table found in database"
        
        if not validate_schema(cursor):
            conn.close()
            return False, "Schema validation failed"
        
        cursor.execute("SELECT COUNT(*) FROM items")
        count = cursor.fetchone()[0]
        conn.close()
        
        return True, f"Valid database with {count} items"
    except sqlite3.Error as e:
        return False, f"SQLite error: {e}"


def generate_catalog(db_path: str) -> dict:
    """Generate the full catalog from a Shop Manager database."""
    
    # Validate first
    is_valid, message = validate_database(db_path)
    if not is_valid:
        raise ValueError(f"Invalid database: {message}")
    print(f"Validation: {message}")
    
    # Connect and extract
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    
    products = extract_products(cursor)
    categories = extract_categories(products)
    version = generate_version()
    version["product_count"] = len(products)
    version["category_count"] = len(categories)
    
    conn.close()
    
    return {
        "products": products,
        "categories": categories,
        "version": version
    }


def copy_path_images(products: list, images_dir: str = LOCAL_IMAGES_DIR):
    """Copy path-based images from Shop Manager static folder to local public/images/ for GitHub Pages."""
    os.makedirs(images_dir, exist_ok=True)
    copied = 0
    for p in products:
        img = p.get("image_url", "")
        if img and not img.startswith("data:"):
            # Extract filename from path like /static/images/items/item_54_xxx.jpg
            filename = img.split("/")[-1] if "/" in img else img
            src = os.path.join(SHOP_STATIC_DIR, filename)
            dst = os.path.join(images_dir, filename)
            if os.path.exists(src) and not os.path.exists(dst):
                import shutil
                shutil.copy2(src, dst)
                copied += 1
    if copied:
        print(f"Copied {copied} images to {images_dir}")


def write_catalog_files(catalog: dict, output_dir: str = DATA_DIR):
    """Write catalog JSON files and copy images for GitHub Pages export."""
    os.makedirs(output_dir, exist_ok=True)

    products_path = os.path.join(output_dir, "products.json")
    with open(products_path, "w", encoding="utf-8") as f:
        json.dump(catalog["products"], f, indent=2, ensure_ascii=False)
    print(f"Written: {products_path} ({len(catalog['products'])} products)")

    categories_path = os.path.join(output_dir, "categories.json")
    with open(categories_path, "w", encoding="utf-8") as f:
        json.dump(catalog["categories"], f, indent=2, ensure_ascii=False)
    print(f"Written: {categories_path} ({len(catalog['categories'])} categories)")

    version_path = os.path.join(output_dir, "version.json")
    with open(version_path, "w", encoding="utf-8") as f:
        json.dump(catalog["version"], f, indent=2, ensure_ascii=False)
    print(f"Written: {version_path} (version {catalog['version']['version']})")

    # Copy path-based images for GitHub Pages
    copy_path_images(catalog["products"])

    return {
        "products": len(catalog["products"]),
        "categories": len(catalog["categories"]),
        "version": catalog["version"]["version"],
        "generated_at": catalog["version"]["generated_at"]
    }


def main():
    """Main entry point."""
    if len(sys.argv) < 2:
        print("Usage: python3 sqlite_to_catalog.py <path-to-shop.db>")
        print("")
        print("Generates customer catalog JSON files from Shop Manager database.")
        print("Only exposes safe fields: id, name, type, description, price, mrp, image_url")
        print("Hidden fields (never exposed): purchase_cost, location, quantity, created_at, updated_at")
        sys.exit(1)
    
    db_path = sys.argv[1]
    
    print(f"Cinema123BW Customer Catalog Generator")
    print(f"=" * 45)
    print(f"Input: {db_path}")
    print(f"Output: {DATA_DIR}")
    print()
    
    try:
        catalog = generate_catalog(db_path)
        stats = write_catalog_files(catalog)
        
        print()
        print("SUCCESS: Catalog generated!")
        print(f"  Products:  {stats['products']}")
        print(f"  Categories: {stats['categories']}")
        print(f"  Version:   {stats['version']}")
        print(f"  Generated: {stats['generated_at']}")
    except ValueError as e:
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"UNEXPECTED ERROR: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
