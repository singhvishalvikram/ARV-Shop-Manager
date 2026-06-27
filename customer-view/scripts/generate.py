#!/usr/bin/env python3
"""
Static Site Generator — reads customer-view.db, generates the public website.
Outputs to /root/customer-view/site/
"""
import sqlite3, json, os, sys, shutil
from datetime import datetime

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CV_DB = os.path.join(ROOT, 'db', 'customer-view.db')
SITE_DIR = os.path.join(ROOT, 'site')
IMG_SRC = os.path.join(ROOT, 'images')


def generate():
    db = sqlite3.connect(CV_DB)
    db.row_factory = sqlite3.Row

    # Read settings
    settings = {}
    for row in db.execute("SELECT key, value FROM settings"):
        settings[row[0]] = row[1]

    # Read visible products
    products = []
    for row in db.execute("""
        SELECT id, name, type, description, price, mrp, discount_percent, image_url,
               visible, featured, badge, sort_order, title_override, description_override, stock_status
        FROM products WHERE visible = 1 ORDER BY sort_order, name
    """):
        p = dict(row)
        if p['title_override']:
            p['name'] = p['title_override']
        if p['description_override']:
            p['description'] = p['description_override']
        # Clean up override fields from output
        del p['title_override']
        del p['description_override']
        del p['visible']
        products.append(p)

    # Read visible categories
    categories = []
    for row in db.execute("SELECT name, display_name, sort_order FROM categories WHERE visible = 1 ORDER BY sort_order"):
        categories.append({
            'name': row[0],
            'display_name': row[1] or row[0],
            'sort_order': row[2]
        })

    # Last sync info
    last_sync = None
    row = db.execute("SELECT timestamp FROM sync_log WHERE action='import' ORDER BY id DESC LIMIT 1").fetchone()
    if row:
        last_sync = row[0]

    db.close()

    # Build site
    os.makedirs(SITE_DIR, exist_ok=True)
    os.makedirs(os.path.join(SITE_DIR, 'images'), exist_ok=True)
    os.makedirs(os.path.join(SITE_DIR, 'data'), exist_ok=True)

    # Copy images
    if os.path.exists(IMG_SRC):
        for f in os.listdir(IMG_SRC):
            src = os.path.join(IMG_SRC, f)
            dst = os.path.join(SITE_DIR, 'images', f)
            if not os.path.exists(dst) or os.path.getmtime(src) > os.path.getmtime(dst):
                shutil.copy2(src, dst)

    # Write data files - SMALL, no base64
    with open(os.path.join(SITE_DIR, 'data', 'products.json'), 'w') as f:
        json.dump(products, f, separators=(',', ':'))
    with open(os.path.join(SITE_DIR, 'data', 'categories.json'), 'w') as f:
        json.dump(categories, f, separators=(',', ':'))
    with open(os.path.join(SITE_DIR, 'data', 'settings.json'), 'w') as f:
        json.dump(settings, f, separators=(',', ':'))
    with open(os.path.join(SITE_DIR, 'data', 'version.json'), 'w') as f:
        json.dump({
            'v': int(datetime.utcnow().timestamp()),
            'at': datetime.utcnow().isoformat(),
            'count': len(products)
        }, f, separators=(',', ':'))

    print(f'Site generated: {len(products)} products, {len(categories)} categories')
    print(f'Site dir: {SITE_DIR}')
    print(f'products.json: {os.path.getsize(os.path.join(SITE_DIR, "data", "products.json"))} bytes')


if __name__ == '__main__':
    generate()
