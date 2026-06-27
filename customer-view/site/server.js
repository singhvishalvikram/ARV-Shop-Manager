#!/usr/bin/env node
/**
 * Customer View - Site Server (port 3000)
 * Serves the static generated site.
 */
const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 3000;
const SITE_DIR = path.join(__dirname, '..', 'site');

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg',
  '.webp': 'image/webp', '.svg': 'image/svg+xml', '.ico': 'image/x-icon',
  '.webmanifest': 'application/manifest+json'
};

const server = http.createServer((req, res) => {
  let urlPath = req.url.split('?')[0].replace(/\.\./g, '');
  if (urlPath === '/') urlPath = '/index.html';
  const filePath = path.join(SITE_DIR, urlPath);
  const ext = path.extname(filePath).toLowerCase();
  const ct = MIME[ext] || 'application/octet-stream';

  fs.readFile(filePath, (err, data) => {
    if (err) { res.writeHead(404); res.end('Not Found'); return; }
    res.writeHead(200, {
      'Content-Type': ct,
      'Content-Length': data.length,
      'Cache-Control': 'no-cache, no-store, must-revalidate',
      'Pragma': 'no-cache',
      'Expires': '0',
      'Access-Control-Allow-Origin': '*'
    });
    res.end(data);
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log('Customer site running on http://localhost:' + PORT);
});
