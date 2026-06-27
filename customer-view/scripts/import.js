#!/usr/bin/env node
/**
 * Import Engine — reads Shop Manager SQLite, extracts base64 images,
 * populates the Customer View DB with its own schema.
 */
const Database = require('better-sqlite3');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const SHOP_DB = process.argv[2] || '/root/shop-manager/backend/shop.db';
const CV_DB = path.join(__dirname, '..', 'db', 'customer-view.db');
const IMG_DIR = path.join(__dirname, '..', 'images');
const SHOP_IMG_DIR = '/root/shop-manager/backend/static/images/items';

function initDB(db) {
  db.exec(`
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
  `);

  // Seed default settings if empty
  const count = db.prepare('SELECT COUNT(*) as c FROM settings').get().c;
  if (count === 0) {
    const defaults = {
      app_title: 'ARV ENTERPRISES',
      app_subtitle: 'Product Catalog',
      whatsapp_number: '919876543210',
      shop_location: '',
      theme_color: '#6366f1',
      footer_text: '',
      show_search: '1',
      show_category_filter: '1',
      show_discount_badges: '1',
      show_mrp: '1',
      show_description: '1',
      show_images: '1',
      show_location: '1',
      products_per_row: '2',
      max_products: '0',
      currency_symbol: '₹',
    };
    const insert = db.prepare('INSERT OR IGNORE INTO settings (key, value) VALUES (?, ?)');
    for (const [k, v] of Object.entries(defaults)) insert.run(k, v);
  }
}

function extractBase64Image(imageUrl, productId) {
  if (!imageUrl || !imageUrl.startsWith('data:')) return imageUrl;
  try {
    const [header, data] = imageUrl.split(',', 2);
    const mime = header.split(':')[1].split(';')[0];
    const extMap = { 'image/jpeg': '.jpg', 'image/png': '.png', 'image/webp': '.webp' };
    const ext = extMap[mime] || '.jpg';
    const filename = `item_${productId}${ext}`;
    const filepath = path.join(IMG_DIR, filename);
    fs.writeFileSync(filepath, Buffer.from(data, 'base64'));
    return `/images/${filename}`;
  } catch (e) {
    console.error(`  Warning: Failed to extract base64 for product ${productId}: ${e.message}`);
    return '';
  }
}

function resolveImageUrl(imageUrl, productId) {
  if (!imageUrl) return '';
  // base64 → extract to file
  if (imageUrl.startsWith('data:')) return extractBase64Image(imageUrl, productId);
  // /static/images/items/item_54_xxx.jpg → /images/item_54_xxx.jpg
  if (imageUrl.startsWith('/static/')) {
    const filename = imageUrl.split('/').pop();
    // Copy from shop static dir if not already in our images dir
    const src = path.join(SHOP_IMG_DIR, filename);
    const dst = path.join(IMG_DIR, filename);
    if (fs.existsSync(src) && !fs.existsSync(dst)) {
      fs.copyFileSync(src, dst);
    }
    return `/images/${filename}`;
  }
  return imageUrl;
}

function importShopData() {
  if (!fs.existsSync(SHOP_DB)) {
    console.error(`Shop DB not found: ${SHOP_DB}`);
    process.exit(1);
  }

  const shopDb = new Database(SHOP_DB, { readonly: true });
  const cvDb = new Database(CV_DB);
  cvDb.pragma('journal_mode = WAL');

  initDB(cvDb);
  fs.mkdirSync(IMG_DIR, { recursive: true });

  // Read all shop items
  const shopItems = shopDb.prepare(`
    SELECT id, name, type, description, price, mrp, image_url, updated_at
    FROM items ORDER BY name ASC
  `).all();
  console.log(`Found ${shopItems.length} products in Shop Manager`);

  // Get existing product IDs to detect removals
  const existingIds = new Set(
    cvDb.prepare('SELECT id FROM products').all().map(r => r.id)
  );

  const insertProduct = cvDb.prepare(`
    INSERT INTO products (id, name, type, description, price, mrp, discount_percent, image_url, visible, featured, sort_order, source_updated_at)
    VALUES (@id, @name, @type, @description, @price, @mrp, @discount, @image_url, 1, 0, @sort, @source_updated_at)
    ON CONFLICT(id) DO UPDATE SET
      name = @name, type = @type, description = @description,
      price = @price, mrp = @mrp, discount_percent = @discount,
      image_url = @image_url, source_updated_at = @source_updated_at,
      updated_at = datetime('now')
  `);

  let added = 0, updated = 0;
  const importedIds = new Set();

  const tx = cvDb.transaction(() => {
    for (const item of shopItems) {
      const price = item.price || 0;
      const mrp = item.mrp || 0;
      const discount = (mrp > price && mrp > 0) ? Math.round(((mrp - price) / mrp) * 100) : 0;
      const imageUrl = resolveImageUrl(item.image_url, item.id);
      const isNew = !existingIds.has(item.id);

      insertProduct.run({
        id: item.id,
        name: item.name || '',
        type: item.type || 'Uncategorized',
        description: item.description || '',
        price, mrp, discount, image_url: imageUrl,
        sort: shopItems.indexOf(item),
        source_updated_at: item.updated_at || '',
      });

      if (isNew) added++; else updated++;
      importedIds.add(item.id);
    }

    // Soft-delete removed products
    const removedIds = [...existingIds].filter(id => !importedIds.has(id));
    if (removedIds.length > 0) {
      cvDb.prepare(`UPDATE products SET visible = 0, updated_at = datetime('now') WHERE id IN (${removedIds.map(() => '?').join(',')})`).run(...removedIds);
    }

    // Sync categories
    const types = [...new Set(shopItems.map(i => i.type || 'Uncategorized'))].sort();
    const catInsert = cvDb.prepare(`INSERT OR IGNORE INTO categories (name, sort_order) VALUES (?, ?)`);
    types.forEach((t, i) => catInsert.run(t, i));

    // Log sync
    cvDb.prepare(`INSERT INTO sync_log (action, products_added, products_updated, products_removed) VALUES (?, ?, ?, ?)`).run(
      'import', added, updated, removedIds.length
    );
  });

  tx();

  const total = cvDb.prepare('SELECT COUNT(*) as c FROM products WHERE visible = 1').get().c;
  console.log(`Import complete: ${added} added, ${updated} updated. Total visible: ${total}`);

  shopDb.close();
  cvDb.close();
}

importShopData();
