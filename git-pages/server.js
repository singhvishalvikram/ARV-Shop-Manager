#!/usr/bin/env node
/**
 * Cinema123BW Customer Catalog - Server
 * Zero-dependency Node.js server.
 * Features: DB auto-watch, cache-busting, image proxy, auto-reload,
 *           View Manager with dedicated SQLite DB.
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const { spawn, execSync } = require('child_process');

const PORT = process.env.PORT || 3000;
const ROOT = __dirname;
const PUBLIC_DIR = path.join(ROOT, 'public');
const DATA_DIR = path.join(ROOT, 'data');
const SCRIPTS_DIR = path.join(ROOT, 'scripts');
const VIEW_DB = path.join(ROOT, 'view-manager.db');
const SHOP_DB = path.join(__dirname, '..', 'shop-manager', 'backend', 'shop.db');
const SHOP_STATIC = path.join(__dirname, '..', 'shop-manager', 'backend', 'static', 'images', 'items');

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.webmanifest': 'application/manifest+json'
};

let ASSET_VERSION = Date.now().toString(36);
let shopProductsCache = [];  // lightweight cache for manager (no images)

function sendJSON(res, data, status = 200) {
  const body = JSON.stringify(data);
  res.writeHead(status, {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-cache, no-store, must-revalidate',
    'Pragma': 'no-cache',
    'Expires': '0'
  });
  res.end(body);
}

function sendStatic(req, res) {
  let urlPath = req.url.split('?')[0];
  urlPath = urlPath.replace(/\.\./g, '');
  let filePath = path.join(PUBLIC_DIR, urlPath);
  if (urlPath === '/') filePath = path.join(PUBLIC_DIR, 'index.html');
  const ext = path.extname(filePath).toLowerCase();
  const contentType = MIME[ext] || 'application/octet-stream';
  fs.readFile(filePath, (err, data) => {
    if (err) { res.writeHead(404); res.end('Not Found'); return; }
    res.writeHead(200, {
      'Content-Type': contentType,
      'Content-Length': data.length,
      'Cache-Control': 'no-cache, no-store, must-revalidate',
      'Pragma': 'no-cache',
      'Expires': '0'
    });
    res.end(data);
  });
}

function serveImage(req, res) {
  let imgPath = req.url.split('?')[0].replace(/\.\./g, '');
  let filePath;
  if (imgPath.startsWith('/images/')) {
    const fileName = imgPath.replace('/images/', '');
    filePath = path.join(PUBLIC_DIR, 'images', fileName);
  } else if (imgPath.startsWith('/static/images/')) {
    const fileName = imgPath.replace('/static/images/items/', '');
    filePath = path.join(SHOP_STATIC, fileName);
  } else { res.writeHead(404); res.end('Not found'); return; }

  const resolved = path.resolve(filePath);
  if (!resolved.startsWith(PUBLIC_DIR) && !resolved.startsWith(SHOP_STATIC)) {
    res.writeHead(403); res.end('Forbidden'); return;
  }

  const ext = path.extname(filePath).toLowerCase();
  fs.readFile(filePath, (err, data) => {
    if (err) { res.writeHead(404); res.end('Image not found'); return; }
    res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream', 'Content-Length': data.length, 'Cache-Control': 'no-cache, no-store, must-revalidate' });
    res.end(data);
  });
}

// --- View Manager DB helpers ---
function getViewConfig() {
  try {
    const rows = execSync(`sqlite3 "${VIEW_DB}" "SELECT key, value FROM settings;"`, { encoding: 'utf-8' }).trim().split('\n');
    const settings = {};
    rows.forEach(row => {
      const idx = row.indexOf('|');
      if (idx > 0) settings[row.substring(0, idx)] = row.substring(idx + 1);
    });
    const hiddenRows = execSync(`sqlite3 "${VIEW_DB}" "SELECT product_id FROM hidden_products;"`, { encoding: 'utf-8' }).trim();
    const hidden = hiddenRows ? hiddenRows.split('\n').map(Number) : [];
    const catRows = execSync(`sqlite3 "${VIEW_DB}" "SELECT category FROM category_filter WHERE visible = 1;"`, { encoding: 'utf-8' }).trim();
    const cats = catRows ? catRows.split('\n') : [];
    return { ...settings, hidden_products: hidden, categories: cats };
  } catch (e) {
    try { return JSON.parse(fs.readFileSync(path.join(PUBLIC_DIR, 'data', 'view-config.json'), 'utf-8')); } catch (e2) { return {}; }
  }
}

function saveViewConfig(config) {
  try {
    for (const [k, v] of Object.entries(config)) {
      if (Array.isArray(v)) continue;
      const val = String(v).replace(/'/g, "''");
      execSync(`sqlite3 "${VIEW_DB}" "INSERT OR REPLACE INTO settings (key, value) VALUES ('${k}', '${val}');"`);
    }
    execSync(`sqlite3 "${VIEW_DB}" "DELETE FROM hidden_products;"`);
    if (config.hidden_products) config.hidden_products.forEach(id => execSync(`sqlite3 "${VIEW_DB}" "INSERT OR IGNORE INTO hidden_products (product_id) VALUES (${id});"`));
    execSync(`sqlite3 "${VIEW_DB}" "DELETE FROM category_filter;"`);
    if (config.categories) config.categories.forEach(cat => {
      const c = cat.replace(/'/g, "''");
      execSync(`sqlite3 "${VIEW_DB}" "INSERT OR IGNORE INTO category_filter (category, visible) VALUES ('${c}', 1);"`);
    });
    fs.writeFileSync(path.join(PUBLIC_DIR, 'data', 'view-config.json'), JSON.stringify(config, null, 2));
    return true;
  } catch (e) {
    fs.writeFileSync(path.join(PUBLIC_DIR, 'data', 'view-config.json'), JSON.stringify(config, null, 2));
    return false;
  }
}

function regenerateCatalog(callback) {
  const script = path.join(SCRIPTS_DIR, 'sqlite_to_catalog.py');
  const child = spawn('python3', [script, SHOP_DB], { cwd: ROOT });
  child.on('close', () => {
    try {
      fs.mkdirSync(path.join(PUBLIC_DIR, 'data'), { recursive: true });
      fs.copyFileSync(path.join(DATA_DIR, 'products.json'), path.join(PUBLIC_DIR, 'data', 'products.json'));
      fs.copyFileSync(path.join(DATA_DIR, 'categories.json'), path.join(PUBLIC_DIR, 'data', 'categories.json'));
      fs.copyFileSync(path.join(DATA_DIR, 'version.json'), path.join(PUBLIC_DIR, 'data', 'version.json'));
      ASSET_VERSION = Date.now().toString(36);
      if (callback) callback(true);
    } catch (e) { if (callback) callback(false); }
  });
}

// --- File upload ---
function handleFileUpload(req, res, callback) {
  const chunks = [];
  req.on('data', c => chunks.push(c));
  req.on('end', () => {
    const buffer = Buffer.concat(chunks);
    const contentType = req.headers['content-type'] || '';
    if (!contentType.includes('multipart/form-data')) { sendJSON(res, { error: 'Expected multipart' }, 400); return; }
    const boundary = contentType.split('boundary=')[1].trim();
    const boundaryBytes = Buffer.from('--' + boundary);
    const headerSep = Buffer.from('\r\n\r\n');
    const firstB = indexOfBuffer(buffer, boundaryBytes, 0);
    if (firstB === -1) { sendJSON(res, { error: 'No boundary' }, 400); return; }
    const afterB = firstB + boundaryBytes.length + 2;
    const headerEnd = indexOfBuffer(buffer, headerSep, afterB);
    if (headerEnd === -1) { sendJSON(res, { error: 'No content' }, 400); return; }
    const header = buffer.subarray(afterB, headerEnd - 2).toString('utf-8');
    if (!header.includes('filename=')) { sendJSON(res, { error: 'No file' }, 400); return; }
    const fileName = header.match(/filename="([^"]+)"/)?.[1] || 'upload.db';
    if (!fileName.endsWith('.db')) { sendJSON(res, { error: 'Must be .db' }, 400); return; }
    const fileStart = headerEnd + headerSep.length;
    const closeB = indexOfBuffer(buffer, boundaryBytes, fileStart);
    if (closeB === -1) { sendJSON(res, { error: 'No end boundary' }, 400); return; }
    let fileEnd = closeB;
    if (fileEnd > fileStart + 1 && buffer[fileEnd-1] === 0x0a && buffer[fileEnd-2] === 0x0d) fileEnd -= 2;
    const fileBuffer = buffer.subarray(fileStart, fileEnd);
    if (fileBuffer.length === 0) { sendJSON(res, { error: 'Empty file' }, 400); return; }
    const tempPath = path.join('/tmp', 'catalog-upload-' + Date.now() + '.db');
    fs.writeFile(tempPath, fileBuffer, (err) => { if (err) { sendJSON(res, { error: 'Save failed' }, 500); return; } callback(tempPath); });
  });
}

function indexOfBuffer(buffer, search, start) {
  start = start || 0;
  for (let i = start; i <= buffer.length - search.length; i++) {
    let found = true;
    for (let j = 0; j < search.length; j++) { if (buffer[i+j] !== search[j]) { found = false; break; } }
    if (found) return i;
  }
  return -1;
}

// --- API handler ---
function handleAPI(req, res) {
  const urlPath = req.url.split('?')[0];

  if (urlPath === '/api/catalog' && req.method === 'GET') {
    try {
      const products = JSON.parse(fs.readFileSync(path.join(PUBLIC_DIR, 'data', 'products.json'), 'utf-8'));
      const categories = JSON.parse(fs.readFileSync(path.join(PUBLIC_DIR, 'data', 'categories.json'), 'utf-8'));
      const version = JSON.parse(fs.readFileSync(path.join(PUBLIC_DIR, 'data', 'version.json'), 'utf-8'));
      sendJSON(res, { products, categories, version, asset_version: ASSET_VERSION });
    } catch (e) { sendJSON(res, { products: [], categories: [], version: { version: 0 }, asset_version: ASSET_VERSION }); }
    return;
  }

  if (urlPath === '/api/version' && req.method === 'GET') {
    try {
      const version = JSON.parse(fs.readFileSync(path.join(PUBLIC_DIR, 'data', 'version.json'), 'utf-8'));
      sendJSON(res, { ...version, asset_version: ASSET_VERSION });
    } catch (e) { sendJSON(res, { version: 0, asset_version: ASSET_VERSION }); }
    return;
  }

  // GET /api/shop-products - all products directly from Shop Manager DB (for manager page)
  if (urlPath === '/api/shop-products' && req.method === 'GET') {
    // Serve from cache (populated by DB watcher), fallback to file
    if (shopProductsCache && shopProductsCache.length > 0) {
      sendJSON(res, shopProductsCache);
    } else {
      try {
        // Quick fallback: read from cached products.json (has images but works)
        const all = JSON.parse(fs.readFileSync(path.join(PUBLIC_DIR, 'data', 'products.json'), 'utf-8'));
        // Strip image_url to keep response small for manager
        shopProductsCache = all.map(p => ({
          id: p.id, name: p.name || '', type: p.type || 'Uncategorized',
          price: p.price || 0, mrp: p.mrp || 0, discount_percent: p.discount_percent || 0
        }));
        sendJSON(res, shopProductsCache);
      } catch (e) { sendJSON(res, []); }
    }
    return;
  }

  if (urlPath === '/api/view-config' && req.method === 'GET') {
    sendJSON(res, getViewConfig());
    return;
  }

  if (urlPath === '/api/view-config' && req.method === 'POST') {
    const chunks = [];
    req.on('data', c => chunks.push(c));
    req.on('end', () => {
      let config = {};
      try { config = JSON.parse(Buffer.concat(chunks).toString()); } catch (e) {}
      const ok = saveViewConfig(config);
      regenerateCatalog(() => sendJSON(res, { success: ok, asset_version: ASSET_VERSION }));
    });
    return;
  }

  if (urlPath === '/api/admin/validate' && req.method === 'POST') {
    handleFileUpload(req, res, (filePath) => {
      const child = spawn('python3', [path.join(SCRIPTS_DIR, 'sqlite_to_catalog.py'), filePath], { cwd: ROOT });
      let stderr = '';
      child.stderr.on('data', d => stderr += d);
      child.on('close', () => {
        fs.unlink(filePath, () => {});
        sendJSON(res, { ok: stderr.includes('SUCCESS'), message: stderr.includes('SUCCESS') ? 'Valid' : stderr.split('\n').find(l => l.startsWith('ERROR')) || 'Invalid' });
      });
    });
    return;
  }

  if (urlPath === '/api/admin/publish' && req.method === 'POST') {
    handleFileUpload(req, res, (filePath) => {
      const child = spawn('python3', [path.join(SCRIPTS_DIR, 'sqlite_to_catalog.py'), filePath], { cwd: ROOT });
      child.on('close', () => { fs.unlink(filePath, () => {}); regenerateCatalog(); sendJSON(res, { success: true }); });
    });
    return;
  }

  // POST /api/generate-catalog - trigger sync from Shop Manager DB
  if (urlPath === '/api/generate-catalog' && req.method === 'POST') {
    regenerateCatalog(() => sendJSON(res, { success: true, asset_version: ASSET_VERSION }));
    refreshShopCache();
    return;
  }

  sendJSON(res, { error: 'Not found' }, 404);
}

// --- DB watcher ---
function refreshShopCache() {
  // Async: rebuild lightweight product cache from Shop DB via Python
  const tmpFile = path.join('/tmp', 'shop-products-' + Date.now() + '.json');
  const child = spawn('python3', [
    '-c',
    "import sqlite3,json,sys; c=sqlite3.connect(sys.argv[1]).cursor(); c.execute('SELECT id,name,type,price,mrp FROM items ORDER BY name'); r=[dict(zip(['id','name','type','price','mrp'],row)) for row in c.fetchall()]; open(sys.argv[2],'w').write(json.dumps(r))",
    SHOP_DB,
    tmpFile
  ]);
  child.on('close', (code) => {
    if (code === 0) {
      try {
        const raw = fs.readFileSync(tmpFile, 'utf-8');
        const rawProducts = JSON.parse(raw);
        shopProductsCache = rawProducts.map(p => {
          const price = parseFloat(p.price) || 0;
          const mrp = parseFloat(p.mrp) || 0;
          const discount = (mrp > price && mrp > 0) ? Math.round(((mrp - price) / mrp) * 100) : 0;
          return { id: p.id, name: p.name || '', type: p.type || 'Uncategorized', price, mrp, discount_percent: discount };
        });
        console.log('[Cache] Shop products refreshed:', shopProductsCache.length, 'items');
      } catch (e) { console.log('[Cache] Failed to parse shop products:', e.message); }
    }
    try { fs.unlinkSync(tmpFile); } catch (e) {}
  });
  child.stderr.on('data', d => console.log('[Cache] Python err:', d.toString().trim()));
}

function startDbWatcher() {
  // Initial cache load
  refreshShopCache();
  let lastMtime = 0;
  try { lastMtime = fs.statSync(SHOP_DB).mtimeMs; } catch (e) {}
  setInterval(() => {
    try {
      const stat = fs.statSync(SHOP_DB);
      if (stat.mtimeMs !== lastMtime) {
        lastMtime = stat.mtimeMs;
        console.log('[Watcher] DB changed, regenerating...');
        regenerateCatalog();
        refreshShopCache();
      }
    } catch (e) {}
  }, 5000);
  console.log('[Watcher] Watching ' + SHOP_DB);
}

// --- Server ---
const server = http.createServer((req, res) => {
  const urlPath = req.url.split('?')[0];
  res.setHeader('Access-Control-Allow-Origin', '*');

  if (req.method === 'OPTIONS') { res.writeHead(204); res.end(); return; }
  if (urlPath.startsWith('/images/') || urlPath.startsWith('/static/images/')) { serveImage(req, res); return; }
  if (urlPath.startsWith('/api/')) { handleAPI(req, res); return; }
  if (urlPath === '/admin') { res.writeHead(302, { 'Location': '/admin.html' }); res.end(); return; }
  sendStatic(req, res);
});

server.listen(PORT, '0.0.0.0', () => {
  console.log('Cinema123BW running on http://localhost:' + PORT);
  startDbWatcher();
});
