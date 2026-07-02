"""
Shop Manager - Flask Backend
SQLite + REST API + Web Frontend + Image Processing
"""
import os
import json
import sqlite3
import base64
import io
from datetime import datetime
from flask import Flask, jsonify, request, render_template, send_from_directory

from domain import compute_stock_status

# Try to import PIL, fallback to manual handling if not available
try:
    from PIL import Image
    HAS_PIL = True
except ImportError:
    HAS_PIL = False
    print("Warning: PIL not available. Images will be stored as-is without optimization.")

app = Flask(__name__, static_folder='static', template_folder='templates')

# ── Config (env-overridable; no hardcoded host paths) ──────────────────
# Defaults are repo-relative so the app runs anywhere; deployments override
# via environment variables (see .env.example). Standards GUARDRAILS §1.4.
_BASE_DIR = os.path.dirname(os.path.abspath(__file__))
_SCRIPTS_DIR = os.path.join(os.path.dirname(_BASE_DIR), 'scripts')

DB_PATH = os.environ.get('SHOP_DB_PATH', os.path.join(_BASE_DIR, 'shop.db'))
IMAGES_DIR = os.environ.get('SHOP_IMAGES_DIR', os.path.join(_BASE_DIR, 'static', 'images', 'items'))

# Create images directory if it doesn't exist
os.makedirs(IMAGES_DIR, exist_ok=True)

# ========== GOOGLE DRIVE AUTO-BACKUP ==========
AUTO_BACKUP_SCRIPT = os.environ.get('AUTO_BACKUP_SCRIPT', os.path.join(_SCRIPTS_DIR, 'auto_backup.py'))
BACKUP_DIR = os.environ.get('BACKUP_DIR', os.path.join(os.path.dirname(_BASE_DIR), 'backups'))
DRIVE_CONFIG = os.environ.get('DRIVE_CONFIG', os.path.join(BACKUP_DIR, '.drive_config.json'))
GDRIVE_FOLDER_ID = os.environ.get('GDRIVE_FOLDER_ID', '1Weo9kErWVbTvcscEURVrG6y3syeWm-mQ')

# Load folder ID from shared config so it stays in sync with backup script
try:
    if os.path.exists(DRIVE_CONFIG):
        with open(DRIVE_CONFIG) as f:
            cfg = json.load(f)
        if cfg.get("folder_id"):
            GDRIVE_FOLDER_ID = cfg["folder_id"]
except Exception:
    pass

def trigger_auto_backup():
    """Trigger Google Drive backup in background (non-blocking)."""
    try:
        import subprocess
        import threading

        def _run_backup():
            try:
                result = subprocess.run(
                    ["python3", AUTO_BACKUP_SCRIPT],
                    capture_output=True, text=True, timeout=120
                )
                if result.returncode == 0:
                    print(f"[AUTO-BACKUP] Success: {result.stdout.strip()[-100:]}")
                else:
                    print(f"[AUTO-BACKUP] Failed: {result.stderr.strip()[-200:]}")
            except subprocess.TimeoutExpired:
                print("[AUTO-BACKUP] Timed out after 120s")
            except Exception as e:
                print(f"[AUTO-BACKUP] Error: {e}")

        # Run in a daemon thread so it doesn't block the API response
        thread = threading.Thread(target=_run_backup, daemon=True)
        thread.start()
        print(f"[AUTO-BACKUP] Triggered at {datetime.now().isoformat()}")
    except Exception as e:
        print(f"[AUTO-BACKUP] Failed to trigger: {e}")


# Track whether a write happened in this request
_write_happened = False

@app.before_request
def before_request():
    global _write_happened
    _write_happened = False

@app.after_request
def after_request(response):
    global _write_happened
    # Trigger backup on successful write operations (2xx status)
    if _write_happened and response.status_code < 300:
        trigger_auto_backup()
    # Force hard refresh - no caching on any response
    response.headers['Cache-Control'] = 'no-cache, no-store, must-revalidate'
    response.headers['Pragma'] = 'no-cache'
    response.headers['Expires'] = '0'
    return response

def mark_write():
    """Call this in any endpoint that modifies data to trigger auto-backup."""
    global _write_happened
    _write_happened = True

@app.route('/api/backup', methods=['POST'])
def manual_backup():
    """Manually trigger a backup to Google Drive."""
    import subprocess
    try:
        result = subprocess.run(
            ["python3", AUTO_BACKUP_SCRIPT],
            capture_output=True, text=True, timeout=60
        )
        if result.returncode == 0:
            return jsonify({'ok': True, 'message': 'Backup triggered', 'output': result.stdout.strip()})
        else:
            return jsonify({'ok': False, 'error': result.stderr.strip()}), 500
    except Exception as e:
        return jsonify({'ok': False, 'error': str(e)}), 500

@app.route('/api/backup/status', methods=['GET'])
def backup_status():
    """Check backup status and list recent backups."""
    import glob
    backup_dir = BACKUP_DIR
    backups = sorted(glob.glob(os.path.join(backup_dir, "shop-manager-backup-*.json")), reverse=True)[:5]
    return jsonify({
        'ok': True,
        'recent_backups': [
            {
                'filename': os.path.basename(b),
                'size': os.path.getsize(b),
                'modified': datetime.fromtimestamp(os.path.getmtime(b)).isoformat()
            }
            for b in backups
        ],
        'total_local_backups': len(glob.glob(os.path.join(backup_dir, "shop-manager-backup-*.json")))
    })


def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    return conn


def process_image(base64_data, item_id):
    """
    Process base64 image: decode, optimize, resize, save to disk.
    Returns relative image path for storage in DB.
    Falls back to storing base64 as data URL if PIL unavailable.
    """
    if not HAS_PIL:
        # Fallback: store as data URL directly
        if ',' not in base64_data:
            base64_data = 'data:image/jpeg;base64,' + base64_data
        return base64_data

    try:
        # Decode base64
        if ',' in base64_data:
            base64_data = base64_data.split(',')[1]
        
        image_bytes = base64.b64decode(base64_data)
        img = Image.open(io.BytesIO(image_bytes))
        
        # Convert RGBA to RGB if necessary
        if img.mode in ('RGBA', 'LA', 'P'):
            background = Image.new('RGB', img.size, (255, 255, 255))
            background.paste(img, mask=img.split()[-1] if img.mode == 'RGBA' else None)
            img = background
        
        # Resize to fit application (max 400x400)
        img.thumbnail((400, 400), Image.Resampling.LANCZOS)
        
        # Save as optimized JPEG
        filename = f"item_{item_id}_{datetime.now().timestamp()}.jpg"
        filepath = os.path.join(IMAGES_DIR, filename)
        
        img.save(filepath, 'JPEG', quality=75, optimize=True)
        
        # Return relative path for web serving
        return f"/static/images/items/{filename}"
    except Exception as e:
        print(f"Image processing error: {e}")
        # Fallback to base64
        if ',' not in base64_data:
            return 'data:image/jpeg;base64,' + base64_data
        return base64_data


def init_db():
    conn = get_db()
    conn.execute("""
        CREATE TABLE IF NOT EXISTS items (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            type TEXT NOT NULL DEFAULT '',
            description TEXT NOT NULL DEFAULT '',
            price REAL NOT NULL DEFAULT 0.0,
            mrp REAL NOT NULL DEFAULT 0.0,
            purchase_cost REAL NOT NULL DEFAULT 0.0,
            image_url TEXT NOT NULL DEFAULT '',
            location TEXT NOT NULL DEFAULT '',
            quantity INTEGER NOT NULL DEFAULT 0,
            created_at TEXT NOT NULL DEFAULT (datetime('now')),
            updated_at TEXT NOT NULL DEFAULT (datetime('now'))
        )
    """)
    conn.execute("""
        CREATE TABLE IF NOT EXISTS daily_sales (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            item_id INTEGER NOT NULL,
            quantity_sold INTEGER NOT NULL,
            sale_price REAL NOT NULL,
            sale_date TEXT NOT NULL DEFAULT (datetime('now')),
            description TEXT NOT NULL DEFAULT '',
            FOREIGN KEY (item_id) REFERENCES items (id)
        )
    """)
    conn.commit()
    conn.close()


# ── API Routes ────────────────────────────────────────────────────────
@app.route('/api/items', methods=['GET'])
def get_items():
    """List all items with optional search (no pagination)."""
    search = request.args.get('search', '').strip()

    conn = get_db()

    if search:
        like = f'%{search}%'
        rows = conn.execute(
            """SELECT * FROM items
               WHERE name LIKE ? OR type LIKE ? OR description LIKE ?
               ORDER BY updated_at DESC""",
            (like, like, like)
        ).fetchall()
    else:
        rows = conn.execute(
            "SELECT * FROM items ORDER BY updated_at DESC"
        ).fetchall()

    items = [dict(row) for row in rows]
    # Add computed stock_status field (shared rule — see domain.py)
    for item in items:
        item['stock_status'] = compute_stock_status(item.get('quantity'))

    return jsonify({'items': items})

@app.route('/api/items/<int:item_id>', methods=['GET'])
def get_item(item_id):
    conn = get_db()
    row = conn.execute("SELECT * FROM items WHERE id = ?", (item_id,)).fetchone()
    conn.close()
    if row is None:
        return jsonify({'error': 'Item not found'}), 404
    item = dict(row)
    item['stock_status'] = compute_stock_status(item.get('quantity'))
    return jsonify(item)


@app.route('/api/items', methods=['POST'])
def create_item():
    data = request.get_json()
    if not data or not data.get('name', '').strip():
        return jsonify({'error': 'Item name is required'}), 400

    # Enforce mandatory fields: type, quantity, price
    if not data.get('type', '').strip():
        return jsonify({'error': 'Item type is required'}), 400

    price = data.get('price')
    if price is None or (isinstance(price, (int, float)) and price <= 0):
        return jsonify({'error': 'Price must be a positive number'}), 400

    quantity = data.get('quantity')
    if quantity is None or (isinstance(quantity, int) and quantity < 0):
        return jsonify({'error': 'Quantity must be 0 or more'}), 400

    conn = get_db()
    cursor = conn.execute(
        """INSERT INTO items (name, type, description, price, mrp, purchase_cost, image_url, location, quantity)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
        (
            data['name'].strip(),
            data['type'].strip(),
            data.get('description', '').strip(),
            float(price),
            float(data.get('mrp', price * 1.2)),
            float(data.get('purchase_cost', price * 0.8)),
            '',  # Placeholder, will be updated if image is provided
            data.get('location', '').strip(),
            int(quantity)
        )
    )
    conn.commit()
    item_id = cursor.lastrowid
    
    # Process image if provided
    image_url = ''
    if data.get('image_base64'):
        image_url = process_image(data['image_base64'], item_id)
        if image_url:
            conn.execute("UPDATE items SET image_url = ? WHERE id = ?", (image_url, item_id))
            conn.commit()
    
    row = conn.execute("SELECT * FROM items WHERE id = ?", (item_id,)).fetchone()
    conn.close()
    mark_write()
    return jsonify(dict(row)), 201


@app.route('/api/items/<int:item_id>', methods=['PUT'])
def update_item(item_id):
    data = request.get_json()
    if not data:
        return jsonify({'error': 'No data provided'}), 400

    conn = get_db()
    row = conn.execute("SELECT * FROM items WHERE id = ?", (item_id,)).fetchone()
    if row is None:
        conn.close()
        return jsonify({'error': 'Item not found'}), 404

    fields = {}
    for key in ('name', 'type', 'description', 'price', 'mrp', 'purchase_cost', 'location', 'quantity'):
        if key in data:
            fields[key] = data[key]

    # Handle image upload separately
    image_url = row['image_url']
    if 'image_base64' in data and data['image_base64']:
        new_image_url = process_image(data['image_base64'], item_id)
        if new_image_url:
            image_url = new_image_url
    elif 'image_url' in data and data['image_url'] == '':
        # Explicitly remove image
        image_url = ''
    fields['image_url'] = image_url

    if not fields:
        conn.close()
        return jsonify({'error': 'No fields to update'}), 400

    fields['updated_at'] = datetime.now().isoformat()
    set_clause = ', '.join(f"{k} = ?" for k in fields)
    values = list(fields.values()) + [item_id]

    conn.execute(f"UPDATE items SET {set_clause} WHERE id = ?", values)
    conn.commit()
    row = conn.execute("SELECT * FROM items WHERE id = ?", (item_id,)).fetchone()
    conn.close()
    mark_write()
    return jsonify(dict(row))


@app.route('/api/items/<int:item_id>', methods=['DELETE'])
def delete_item(item_id):
    conn = get_db()
    row = conn.execute("SELECT * FROM items WHERE id = ?", (item_id,)).fetchone()
    if row is None:
        conn.close()
        return jsonify({'error': 'Item not found'}), 404
    conn.execute("DELETE FROM items WHERE id = ?", (item_id,))
    conn.commit()
    conn.close()
    mark_write()
    return jsonify({'ok': True})


@app.route('/api/sales', methods=['POST'])
def create_sale():
    """Record a sale: reduce item quantity and log in daily_sales."""
    data = request.get_json()
    if not data:
        return jsonify({'error': 'No data provided'}), 400

    required = ['item_id', 'quantity', 'price']
    for field in required:
        if field not in data:
            return jsonify({'error': f'{field} is required'}), 400

    try:
        item_id = int(data['item_id'])
        quantity = int(data['quantity'])
        price = float(data['price'])
        if quantity <= 0:
            return jsonify({'error': 'Quantity must be positive'}), 400
        if price < 0:
            return jsonify({'error': 'Price cannot be negative'}), 400
    except ValueError:
        return jsonify({'error': 'Invalid number format'}), 400

    description = data.get('description', '').strip()

    conn = get_db()
    # Check item exists and has sufficient quantity
    item = conn.execute("SELECT quantity, price FROM items WHERE id = ?", (item_id,)).fetchone()
    if item is None:
        conn.close()
        return jsonify({'error': 'Item not found'}), 404

    current_quantity = item['quantity']
    if quantity > current_quantity:
        conn.close()
        return jsonify({'error': 'Not enough stock'}), 400

    # Update item quantity
    new_quantity = current_quantity - quantity
    conn.execute(
        "UPDATE items SET quantity = ?, updated_at = ? WHERE id = ?",
        (new_quantity, datetime.now().isoformat(), item_id)
    )
    # Log sale
    conn.execute(
        """INSERT INTO daily_sales (item_id, quantity_sold, sale_price, description)
           VALUES (?, ?, ?, ?)""",
        (item_id, quantity, price, description)
    )
    conn.commit()
    conn.close()
    mark_write()
    return jsonify({'ok': True, 'message': 'Sale recorded'}), 201


@app.route('/api/sales', methods=['GET'])
def get_sales():
    """Get sales records. Optional query params: date (YYYY-MM-DD)."""
    date_filter = request.args.get('date', '').strip()

    conn = get_db()
    if date_filter:
        rows = conn.execute(
            """SELECT s.*, i.name as item_name, i.type as item_type
               FROM daily_sales s
               JOIN items i ON s.item_id = i.id
               WHERE DATE(s.sale_date) = ?
               ORDER BY s.sale_date DESC""",
            (date_filter,)
        ).fetchall()
    else:
        rows = conn.execute(
            """SELECT s.*, i.name as item_name, i.type as item_type
               FROM daily_sales s
               JOIN items i ON s.item_id = i.id
               ORDER BY s.sale_date DESC
               LIMIT 100"""
        ).fetchall()

    total_revenue = conn.execute(
        "SELECT COALESCE(SUM(quantity_sold * sale_price), 0) as r FROM daily_sales"
    ).fetchone()['r']

    today_revenue_row = conn.execute(
        """SELECT COALESCE(SUM(quantity_sold * sale_price), 0) as r
           FROM daily_sales WHERE DATE(sale_date) = DATE('now')"""
    ).fetchone()
    today_revenue = today_revenue_row['r'] if today_revenue_row else 0

    conn.close()

    sales = [dict(r) for r in rows]
    return jsonify({
        'sales': sales,
        'total_revenue': round(total_revenue, 2),
        'today_revenue': round(today_revenue, 2),
        'count': len(sales)
    })


@app.route('/api/dashboard', methods=['GET'])
def dashboard():
    conn = get_db()
    total_items = conn.execute("SELECT COUNT(*) as c FROM items").fetchone()['c']

    type_breakdown = [
        dict(r) for r in conn.execute(
            "SELECT type, COUNT(*) as count FROM items WHERE type != '' GROUP BY type ORDER BY count DESC"
        ).fetchall()
    ]

    recent_items = [
        dict(r) for r in conn.execute(
            "SELECT * FROM items ORDER BY created_at DESC LIMIT 5"
        ).fetchall()
    ]

    # Calculate total stock value at purchase cost and at MRP
    stock_value_row = conn.execute(
        "SELECT COALESCE(SUM(price * quantity), 0) as v FROM items"
    ).fetchone()
    total_stock_value = stock_value_row['v']

    stock_cost_row = conn.execute(
        "SELECT COALESCE(SUM(purchase_cost * quantity), 0) as v FROM items"
    ).fetchone()
    total_stock_cost = stock_cost_row['v']

    stock_mrp_row = conn.execute(
        "SELECT COALESCE(SUM(mrp * quantity), 0) as v FROM items"
    ).fetchone()
    total_stock_mrp = stock_mrp_row['v']

    # Average price (mean of price, ignoring quantity)
    avg_price_row = conn.execute("SELECT COALESCE(AVG(price), 0) as a FROM items").fetchone()
    avg_price = avg_price_row['a']

    # Total quantity in stock
    total_quantity_row = conn.execute("SELECT COALESCE(SUM(quantity), 0) as q FROM items").fetchone()
    total_quantity = total_quantity_row['q']

    # Today's sales revenue
    today_rev_row = conn.execute(
        """SELECT COALESCE(SUM(quantity_sold * sale_price), 0) as r
           FROM daily_sales WHERE DATE(sale_date) = DATE('now')"""
    ).fetchone()
    today_revenue = today_rev_row['r'] if today_rev_row else 0

    # Today's items sold
    today_sold_row = conn.execute(
        """SELECT COALESCE(SUM(quantity_sold), 0) as q
           FROM daily_sales WHERE DATE(sale_date) = DATE('now')"""
    ).fetchone()
    today_sold = today_sold_row['q'] if today_sold_row else 0

    conn.close()

    stats = {
        'total_items': total_items,
        'total_types': len(type_breakdown),
        'total_quantity': total_quantity,
        'avg_price': round(avg_price, 2),
        'total_stock_value': round(total_stock_value, 2),
        'total_stock_cost': round(total_stock_cost, 2),
        'total_stock_mrp': round(total_stock_mrp, 2),
        'today_revenue': round(today_revenue, 2),
        'today_sold': today_sold,
        'type_breakdown': type_breakdown,
        'recent_items': recent_items,
    }
    return jsonify(stats)


# ── Frontend Routes ───────────────────────────────────────────────────
@app.route('/')
def index():
    return render_template('index.html')


@app.route('/manifest.json')
def manifest():
    return send_from_directory(app.static_folder, 'manifest.json')


if __name__ == '__main__':
    init_db()
    app.run(host='0.0.0.0', port=8080, debug=True)