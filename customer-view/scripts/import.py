#!/usr/bin/env python3
"""
Import Engine — reads Shop Manager SQLite, extracts base64 images to files,
populates the Customer View DB with its own schema.
"""
import sqlite3, json, os, sys, base64, shutil
from datetime import datetime

SHOP_DB = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'shop-manager', 'backend', 'shop.db')
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CV_DB = os.path.join(ROOT, 'db', 'customer-view.db')
IMG_DIR = os.path.join(ROOT, 'images')
SITE_DIR = os.path.join(ROOT, 'site')
SHOP_IMG_DIR = os.path.join(ROOT, 'shop-manager', 'backend', 'static', 'images', 'items')


def init_db(db):
    db.executescript("""
    CREATE TABLE IF NOT EXISTS products (
      id INTEGER PRIMARY KEY,
      name TEXT NOT NULL,
      type TEXT DEFAULT 'Uncategorized',
      description TEXT DEFAULT '',
      price REAL DEFAULT 0,
      mrp REAL DEFAULT 0,
      discount_percent INTEGER DEFAULT 0,
      image_url TEXT DEFAULT '',
      visible INTEGER DEFAULT 1,
      featured INTEGER DEFAULT 0,
      badge TEXT DEFAULT '',
      sort_order INTEGER DEFAULT 0,
      title_override TEXT DEFAULT '',
      description_override TEXT DEFAULT '',
      source_updated_at TEXT DEFAULT '',
      stock_status TEXT DEFAULT 'in_stock',
      imported_at TEXT DEFAULT (datetime('now')),
      updated_at TEXT DEFAULT (datetime('now'))
    );
    CREATE TABLE IF NOT EXISTS categories (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL UNIQUE,
      display_name TEXT DEFAULT '',
      visible INTEGER DEFAULT 1,
      sort_order INTEGER DEFAULT 0
    );
    CREATE TABLE IF NOT EXISTS settings (
      key TEXT PRIMARY KEY,
      value TEXT DEFAULT ''
    );
    CREATE TABLE IF NOT EXISTS sync_log (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      timestamp TEXT DEFAULT (datetime('now')),
      action TEXT NOT NULL,
      products_added INTEGER DEFAULT 0,
      products_updated INTEGER DEFAULT 0,
      products_removed INTEGER DEFAULT 0,
      details TEXT DEFAULT ''
    );
    CREATE INDEX IF NOT EXISTS idx_products_visible ON products(visible);
    CREATE INDEX IF NOT EXISTS idx_products_featured ON products(featured);
    CREATE INDEX IF NOT EXISTS idx_products_type ON products(type);

    CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      phone TEXT NOT NULL UNIQUE,
      name TEXT DEFAULT '',
      password_hash TEXT NOT NULL,
      is_guest INTEGER DEFAULT 0,
      created_at TEXT DEFAULT (datetime('now')),
      last_login TEXT DEFAULT (datetime('now'))
    );

    CREATE TABLE IF NOT EXISTS user_cart (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id INTEGER NOT NULL,
      product_id INTEGER NOT NULL,
      qty INTEGER DEFAULT 1,
      added_at TEXT DEFAULT (datetime('now')),
      UNIQUE(user_id, product_id),
      FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
      FOREIGN KEY(product_id) REFERENCES products(id) ON DELETE CASCADE
    );

    CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);
    CREATE INDEX IF NOT EXISTS idx_cart_user ON user_cart(user_id);

    CREATE TABLE IF NOT EXISTS sessions (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id INTEGER NOT NULL,
      token TEXT NOT NULL UNIQUE,
      expires TEXT NOT NULL,
      FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
    );
    CREATE INDEX IF NOT EXISTS idx_sessions_token ON sessions(token);
  """)

    count = db.execute("SELECT COUNT(*) FROM settings").fetchone()[0]
    if count == 0:
        defaults = {
            'app_title': 'ARV ENTERPRISES', 'app_subtitle': 'Product Catalog',
            'whatsapp_number': '919876543210', 'shop_location': '',
            'theme_color': '#6366f1', 'footer_text': '',
            'show_search': '1', 'show_category_filter': '1',
            'show_discount_badges': '1', 'show_mrp': '1',
            'show_description': '1', 'show_images': '1',
            'show_location': '1', 'products_per_row': '2',
            'max_products': '0', 'currency_symbol': '₹',
        }
        for k, v in defaults.items():
            db.execute("INSERT OR IGNORE INTO settings (key, value) VALUES (?, ?)", (k, v))


def resolve_image_url(image_url, product_id):
    if not image_url:
        return ''
    if image_url.startswith('data:'):
        try:
            header, data = image_url.split(',', 1)
            mime = header.split(':')[1].split(';')[0]
            ext = {'image/jpeg': '.jpg', 'image/png': '.png', 'image/webp': '.webp'}.get(mime, '.jpg')
            filename = f'item_{product_id}{ext}'
            filepath = os.path.join(IMG_DIR, filename)
            os.makedirs(IMG_DIR, exist_ok=True)
            with open(filepath, 'wb') as f:
                f.write(base64.b64decode(data))
            return f'/images/{filename}'
        except Exception as e:
            print(f'  Warning: base64 extract failed for {product_id}: {e}')
            return ''
    if image_url.startswith('/static/'):
        filename = image_url.split('/')[-1]
        src = os.path.join(SHOP_IMG_DIR, filename)
        dst = os.path.join(IMG_DIR, filename)
        os.makedirs(IMG_DIR, exist_ok=True)
        if os.path.exists(src) and not os.path.exists(dst):
            shutil.copy2(src, dst)
        return f'/images/{filename}'
    return image_url


def import_shop_data():
    if not os.path.exists(SHOP_DB):
        print(f'Shop DB not found: {SHOP_DB}')
        sys.exit(1)

    shop = sqlite3.connect(SHOP_DB)
    shop.row_factory = sqlite3.Row
    cv = sqlite3.connect(CV_DB)
    init_db(cv)
    os.makedirs(IMG_DIR, exist_ok=True)

    items = shop.execute("""
        SELECT id, name, type, description, price, mrp, image_url, quantity, updated_at
        FROM items ORDER BY name ASC
    """).fetchall()
    print(f'Found {len(items)} products in Shop Manager')

    existing_ids = set(r[0] for r in cv.execute('SELECT id FROM products').fetchall())
    added = updated = 0
    imported_ids = set()

    for idx, item in enumerate(items):
        price = item['price'] or 0
        mrp = item['mrp'] or 0
        discount = round(((mrp - price) / mrp) * 100) if (mrp > price and mrp > 0) else 0
        img = resolve_image_url(item['image_url'], item['id'])
        is_new = item['id'] not in existing_ids
        stock_status = 'out_of_stock' if (item['quantity'] or 0) <= 0 else 'in_stock'

        cv.execute("""
            INSERT INTO products (id, name, type, description, price, mrp, discount_percent, image_url, visible, featured, sort_order, source_updated_at, stock_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 0, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
              name=excluded.name, type=excluded.type, description=excluded.description,
              price=excluded.price, mrp=excluded.mrp, discount_percent=excluded.discount_percent,
              image_url=excluded.image_url, source_updated_at=excluded.source_updated_at,
              stock_status=excluded.stock_status,
              updated_at=datetime('now')
        """, (item['id'], item['name'] or '', item['type'] or 'Uncategorized',
              item['description'] or '', price, mrp, discount, img, idx,
              item['updated_at'] or '', stock_status))

        if is_new:
            added += 1
        else:
            updated += 1
        imported_ids.add(item['id'])

    removed = existing_ids - imported_ids
    if removed:
        cv.execute(f"UPDATE products SET visible=0, updated_at=datetime('now') WHERE id IN ({','.join('?' * len(removed))})", list(removed))

    types = sorted(set(i['type'] or 'Uncategorized' for i in items))
    for i, t in enumerate(types):
        cv.execute("INSERT OR IGNORE INTO categories (name, sort_order) VALUES (?, ?)", (t, i))

    cv.execute("INSERT INTO sync_log (action, products_added, products_updated, products_removed) VALUES (?, ?, ?, ?)",
               ('import', added, updated, len(removed)))
    cv.commit()

    total = cv.execute("SELECT COUNT(*) FROM products WHERE visible=1").fetchone()[0]
    print(f'Import complete: {added} added, {updated} updated, {len(removed)} removed. Visible: {total}')

    shop.close()
    cv.close()


if __name__ == '__main__':
    import_shop_data()
