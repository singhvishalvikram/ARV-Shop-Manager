const http = require('http');
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');
const crypto = require('crypto');

const PORT = 3001;
const DB_PATH = path.join(__dirname, '..', 'db', 'customer-view.db');
const IMAGES_DIR = path.join(__dirname, '..', 'images');
const IMPORT_SCRIPT = path.join(__dirname, '..', 'scripts', 'import.py');
const MANAGER_HTML = path.join(__dirname, 'manager.html');
const AUTH_SECRET = 'cv_secret_key_2026';

function hashPwd(pwd) {
  return crypto.createHash('sha256').update(pwd + AUTH_SECRET).digest('hex');
}
function genToken() {
  return crypto.randomBytes(32).toString('hex');
}

const TMP_SCRIPT = '/tmp/_db_query.py';

function runPython(script) {
  fs.writeFileSync(TMP_SCRIPT, script);
  const result = execSync(`python3 "${TMP_SCRIPT}"`, { encoding: 'utf8', timeout: 30000 });
  return result.trim();
}

function dbQuery(sql, params = []) {
  const escaped = params.map(p => {
    if (typeof p === 'string') return `'${p.replace(/'/g, "''")}'`;
    return String(p);
  });
  const script = `
import sqlite3, json
conn = sqlite3.connect('${DB_PATH}')
conn.row_factory = sqlite3.Row
cur = conn.cursor()
cur.execute("""${sql}"""${params.length ? `, [${escaped.join(', ')}]` : ''})
rows = cur.fetchall()
conn.close()
print(json.dumps([dict(r) for r in rows], default=str))
`;
  return JSON.parse(runPython(script));
}

function dbQueryOne(sql, params = []) {
  const rows = dbQuery(sql, params);
  return rows.length ? rows[0] : null;
}

function dbExecute(sql, params = []) {
  const escaped = params.map(p => {
    if (typeof p === 'string') return `'${p.replace(/'/g, "''")}'`;
    return String(p);
  });
  const script = `
import sqlite3, json
conn = sqlite3.connect('${DB_PATH}')
cur = conn.cursor()
cur.execute("""${sql}"""${params.length ? `, [${escaped.join(', ')}]` : ''})
conn.commit()
print(json.dumps({"changes": cur.rowcount}))
conn.close()
`;
  return JSON.parse(runPython(script));
}

function parseBody(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
      try { resolve(body ? JSON.parse(body) : {}); } catch (e) { reject(e); }
    });
  });
}

function sendJson(res, data, status = 200) {
  const json = JSON.stringify(data);
  res.writeHead(status, {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(json),
    'Cache-Control': 'no-cache, no-store, must-revalidate',
    'Pragma': 'no-cache',
    'Expires': '0',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization'
  });
  res.end(json);
}

function serveFile(res, filePath, contentType) {
  try {
    const data = fs.readFileSync(filePath);
    res.writeHead(200, {
      'Content-Type': contentType,
      'Content-Length': data.length,
      'Cache-Control': 'no-cache, no-store, must-revalidate',
      'Pragma': 'no-cache',
      'Expires': '0'
    });
    res.end(data);
  } catch (e) {
    res.writeHead(404); res.end('Not found');
  }
}

const MIME = { '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css', '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.gif': 'image/gif', '.svg': 'image/svg+xml', '.webp': 'image/webp', '.ico': 'image/x-icon' };

// Get or create guest user based on device fingerprint
function getGuestUser(req) {
  const cookie = req.headers.cookie || '';
  const match = cookie.match(/cv_guest=([^;]+)/);
  if (match) {
    const guest = dbQueryOne('SELECT id, phone, name, is_guest FROM users WHERE id = ? AND is_guest = 1', [match[1]]);
    if (guest) return guest;
  }
  return null;
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  const pathname = url.pathname;

  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization'
    });
    return res.end();
  }

  // ===== AUTH ENDPOINTS =====
  if (pathname.startsWith('/api/auth/')) {
    try {
      // POST /api/auth/signup - create real account
      if (pathname === '/api/auth/signup' && req.method === 'POST') {
        const body = await parseBody(req);
        if (!body.phone || !body.password) return sendJson(res, { error: 'Phone and password required' }, 400);
        if (body.password.length < 4) return sendJson(res, { error: 'Password must be at least 4 characters' }, 400);
        const existing = dbQueryOne('SELECT id FROM users WHERE phone = ? AND is_guest = 0', [body.phone]);
        if (existing) return sendJson(res, { error: 'Phone already registered' }, 409);
        // If guest exists with this phone, upgrade to real account
        const guest = dbQueryOne('SELECT id FROM users WHERE phone = ? AND is_guest = 1', [body.phone]);
        if (guest) {
          dbExecute('UPDATE users SET name = ?, password_hash = ?, is_guest = 0 WHERE id = ?', [body.name || '', hashPwd(body.password), guest.id]);
          const user = dbQueryOne('SELECT id, phone, name, is_guest FROM users WHERE id = ?', [guest.id]);
          const token = genToken();
          dbExecute('INSERT INTO sessions (user_id, token, expires) VALUES (?, ?, datetime("now", "+30 days"))', [user.id, token]);
          return sendJson(res, { user, token });
        }
        dbExecute('INSERT INTO users (phone, name, password_hash, is_guest) VALUES (?, ?, ?, 0)', [body.phone, body.name || '', hashPwd(body.password)]);
        const user = dbQueryOne('SELECT id, phone, name, is_guest FROM users WHERE phone = ?', [body.phone]);
        const token = genToken();
        dbExecute('INSERT INTO sessions (user_id, token, expires) VALUES (?, ?, datetime("now", "+30 days"))', [user.id, token]);
        return sendJson(res, { user, token });
      }

      // POST /api/auth/login
      if (pathname === '/api/auth/login' && req.method === 'POST') {
        const body = await parseBody(req);
        if (!body.phone || !body.password) return sendJson(res, { error: 'Phone and password required' }, 400);
        const user = dbQueryOne('SELECT id, phone, name, password_hash, is_guest FROM users WHERE phone = ? AND is_guest = 0', [body.phone]);
        if (!user) return sendJson(res, { error: 'Invalid phone or password' }, 401);
        if (user.password_hash !== hashPwd(body.password)) return sendJson(res, { error: 'Invalid phone or password' }, 401);
        dbExecute('UPDATE users SET last_login = datetime("now") WHERE id = ?', [user.id]);
        const token = genToken();
        dbExecute('INSERT INTO sessions (user_id, token, expires) VALUES (?, ?, datetime("now", "+30 days"))', [user.id, token]);
        return sendJson(res, { user: { id: user.id, phone: user.phone, name: user.name, is_guest: user.is_guest }, token });
      }

      // POST /api/auth/guest - create or continue as guest (no signup needed)
      if (pathname === '/api/auth/guest' && req.method === 'POST') {
        const body = await parseBody(req);
        const deviceId = body.deviceId || crypto.randomBytes(16).toString('hex');
        // Check if guest with this deviceId already exists
        let guest = dbQueryOne('SELECT id, phone, name, is_guest FROM users WHERE phone = ? AND is_guest = 1', ['guest_' + deviceId]);
        if (!guest) {
          dbExecute('INSERT INTO users (phone, name, password_hash, is_guest) VALUES (?, ?, ?, 1)', ['guest_' + deviceId, 'Guest', hashPwd(deviceId)]);
          guest = dbQueryOne('SELECT id, phone, name, is_guest FROM users WHERE phone = ?', ['guest_' + deviceId]);
        }
        const token = genToken();
        dbExecute('INSERT INTO sessions (user_id, token, expires) VALUES (?, ?, datetime("now", "+30 days"))', [guest.id, token]);
        return sendJson(res, { user: { id: guest.id, phone: guest.phone, name: guest.name, is_guest: 1 }, token, deviceId });
      }

      // POST /api/auth/logout
      if (pathname === '/api/auth/logout' && req.method === 'POST') {
        const cookie = req.headers.cookie || '';
        const match = cookie.match(/cv_token=([^;]+)/);
        if (match) dbExecute('DELETE FROM sessions WHERE token = ?', [match[1]]);
        return sendJson(res, { success: true });
      }

      // GET /api/auth/me
      if (pathname === '/api/auth/me' && req.method === 'GET') {
        const cookie = req.headers.cookie || '';
        const match = cookie.match(/cv_token=([^;]+)/);
        if (!match) return sendJson(res, { user: null });
        const rows = dbQuery(
          'SELECT u.id, u.phone, u.name, u.is_guest FROM sessions s JOIN users u ON s.user_id = u.id WHERE s.token = ? AND s.expires > datetime("now")',
          [match[1]]
        );
        return sendJson(res, { user: rows.length ? rows[0] : null });
      }

      return sendJson(res, { error: 'Not found' }, 404);
    } catch (e) {
      console.error('Auth Error:', e.message);
      return sendJson(res, { error: e.message }, 500);
    }
  }

  // ===== USER CART ENDPOINTS =====
  if (pathname.startsWith('/api/cart')) {
    try {
      // Support both logged-in users and guests
      let userId = null;
      const cookie = req.headers.cookie || '';
      const tokenMatch = cookie.match(/cv_token=([^;]+)/);
      if (tokenMatch) {
        const userRows = dbQuery(
          'SELECT u.id FROM sessions s JOIN users u ON s.user_id = u.id WHERE s.token = ? AND s.expires > datetime("now")',
          [tokenMatch[1]]
        );
        if (userRows.length) userId = userRows[0].id;
      }
      if (!userId) return sendJson(res, { error: 'Not logged in' }, 401);

      if (req.method === 'GET') {
        const items = dbQuery(
          'SELECT c.product_id, c.qty, p.name, p.price, p.mrp, p.discount_percent, p.image_url FROM user_cart c JOIN products p ON c.product_id = p.id WHERE c.user_id = ? ORDER BY c.added_at DESC',
          [userId]
        );
        return sendJson(res, items);
      }

      if (req.method === 'POST') {
        const body = await parseBody(req);
        if (!body.product_id) return sendJson(res, { error: 'product_id required' }, 400);
        const qty = Math.max(1, Math.min(99, parseInt(body.qty) || 1));
        dbExecute(
          'INSERT INTO user_cart (user_id, product_id, qty) VALUES (?, ?, ?) ON CONFLICT(user_id, product_id) DO UPDATE SET qty = ?',
          [userId, body.product_id, qty, qty]
        );
        return sendJson(res, { success: true });
      }

      if (req.method === 'DELETE') {
        const productId = parseInt(pathname.split('/').pop());
        dbExecute('DELETE FROM user_cart WHERE user_id = ? AND product_id = ?', [userId, productId]);
        return sendJson(res, { success: true });
      }

      return sendJson(res, { error: 'Not found' }, 404);
    } catch (e) {
      console.error('Cart Error:', e.message);
      return sendJson(res, { error: e.message }, 500);
    }
  }

  // ===== MANAGER API =====
  if (pathname.startsWith('/api/')) {
    try {
      if (pathname === '/api/dashboard' && req.method === 'GET') {
        const total = dbQueryOne('SELECT COUNT(*) as count FROM products').count;
        const visible = dbQueryOne('SELECT COUNT(*) as count FROM products WHERE visible = 1').count;
        const hidden = total - visible;
        const categories = dbQuery('SELECT COUNT(*) as count FROM categories WHERE visible = 1')[0].count;
        const lastSync = dbQueryOne('SELECT * FROM sync_log ORDER BY id DESC LIMIT 1');
        const lastPublish = dbQueryOne("SELECT * FROM settings WHERE key = 'last_publish'");
        return sendJson(res, {
          totalProducts: total, visibleProducts: visible, hiddenProducts: hidden,
          categories, lastSync: lastSync ? lastSync.timestamp : null,
          lastPublish: lastPublish ? lastPublish.value : null
        });
      }

      if (pathname === '/api/products' && req.method === 'GET') {
        const products = dbQuery(
          'SELECT id, name, type, price, mrp, discount_percent, visible, featured, badge, sort_order FROM products ORDER BY sort_order ASC, id ASC'
        );
        return sendJson(res, products);
      }

      if (pathname.startsWith('/api/products/') && req.method === 'POST') {
        const id = parseInt(pathname.split('/').pop());
        if (isNaN(id)) return sendJson(res, { error: 'Invalid product ID' }, 400);
        const body = await parseBody(req);
        const fields = [];
        const values = [];
        const allowed = ['visible', 'featured', 'badge', 'title_override', 'description_override', 'sort_order'];
        for (const key of allowed) {
          if (body[key] !== undefined) { fields.push(`${key} = ?`); values.push(body[key]); }
        }
        if (fields.length === 0) return sendJson(res, { error: 'No fields to update' }, 400);
        fields.push("updated_at = datetime('now')");
        values.push(id);
        dbExecute(`UPDATE products SET ${fields.join(', ')} WHERE id = ?`, values);
        return sendJson(res, { success: true });
      }

      if (pathname === '/api/settings' && req.method === 'GET') {
        const rows = dbQuery('SELECT key, value FROM settings');
        const settings = {};
        for (const row of rows) settings[row.key] = row.value;
        return sendJson(res, settings);
      }

      if (pathname === '/api/settings' && req.method === 'POST') {
        const body = await parseBody(req);
        for (const [key, value] of Object.entries(body)) {
          dbExecute('INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value', [key, String(value)]);
        }
        return sendJson(res, { success: true });
      }

      if (pathname === '/api/categories' && req.method === 'GET') {
        return sendJson(res, dbQuery('SELECT * FROM categories ORDER BY sort_order ASC, id ASC'));
      }

      if (pathname === '/api/categories' && req.method === 'POST') {
        const body = await parseBody(req);
        if (!body.id) return sendJson(res, { error: 'Category ID required' }, 400);
        const fields = [];
        const values = [];
        const allowed = ['visible', 'display_name', 'sort_order'];
        for (const key of allowed) {
          if (body[key] !== undefined) { fields.push(`${key} = ?`); values.push(body[key]); }
        }
        if (fields.length === 0) return sendJson(res, { error: 'No fields to update' }, 400);
        values.push(body.id);
        dbExecute(`UPDATE categories SET ${fields.join(', ')} WHERE id = ?`, values);
        return sendJson(res, { success: true });
      }

      if (pathname === '/api/sync' && req.method === 'POST') {
        try {
          const output = execSync(`python3 "${IMPORT_SCRIPT}"`, { encoding: 'utf8', timeout: 120000 });
          try { execSync('python3 /root/customer-view/scripts/generate.py', { encoding: 'utf8', timeout: 30000 }); } catch (e) {}
          return sendJson(res, { success: true, output: output.substring(0, 2000) });
        } catch (e) {
          return sendJson(res, { success: false, error: e.message, output: e.stdout || '' }, 500);
        }
      }

      if (pathname === '/api/publish' && req.method === 'POST') {
        try {
          const genOut = execSync('python3 /root/customer-view/scripts/generate.py', { encoding: 'utf8', timeout: 30000 });
          console.log('[Publish] Generate:', genOut.trim());
          const siteDir = '/root/customer-view/site';
          const repoDir = '/root/Cinema123BW-Customer-Catalog/public';
          execSync(`cp "${siteDir}/index.html" "${repoDir}/index.html"`);
          execSync(`cp "${siteDir}/manifest.json" "${repoDir}/manifest.json" 2>/dev/null; true`);
          execSync(`cp "${siteDir}/data/"*.json "${repoDir}/data/"`);
          execSync(`cp -u "${siteDir}/images/"*.jpg "${repoDir}/images/" 2>/dev/null; true`);
          console.log('[Publish] Files copied to repo');
          const gitDir = '/root/Cinema123BW-Customer-Catalog';
          execSync(`git -C "${gitDir}" add -A`);
          const now = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
          try {
            execSync(`git -C "${gitDir}" commit -m "Publish ${now}"`, { encoding: 'utf8', timeout: 10000 });
            console.log('[Publish] Committed');
          } catch (e) {
            if (e.stdout && e.stdout.includes('nothing to commit')) {
              console.log('[Publish] No changes to commit');
            } else { throw e; }
          }
          execSync(`git -C "${gitDir}" push origin main`, { encoding: 'utf8', timeout: 30000 });
          console.log('[Publish] Pushed to GitHub');
          dbExecute("INSERT INTO settings (key, value) VALUES ('last_publish', datetime('now')) ON CONFLICT(key) DO UPDATE SET value = datetime('now')");
          return sendJson(res, { success: true, message: 'Published! Site will be live in ~30s.' });
        } catch (e) {
          console.error('[Publish] Error:', e.message);
          return sendJson(res, { success: false, error: e.message.slice(0, 500) }, 500);
        }
      }

      return sendJson(res, { error: 'Not found' }, 404);
    } catch (e) {
      console.error('API Error:', e.message);
      return sendJson(res, { error: e.message }, 500);
    }
  }

  if (pathname.startsWith('/images/')) {
    const imgPath = path.join(IMAGES_DIR, pathname.replace('/images/', ''));
    const ext = path.extname(imgPath).toLowerCase();
    serveFile(res, imgPath, MIME[ext] || 'application/octet-stream');
    return;
  }

  if (pathname === '/' || pathname === '/manager.html') {
    try {
      const html = fs.readFileSync(MANAGER_HTML, 'utf8');
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Content-Length': Buffer.byteLength(html), 'Cache-Control': 'no-cache, no-store, must-revalidate', 'Pragma': 'no-cache', 'Expires': '0' });
      return res.end(html);
    } catch (e) {
      res.writeHead(500); return res.end('Error loading manager.html');
    }
  }

  res.writeHead(404); res.end('Not found');
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Manager + Auth server running at http://localhost:${PORT}`);
});
