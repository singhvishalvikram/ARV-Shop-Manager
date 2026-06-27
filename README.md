# ARV Shop Manager

Complete shop management system with inventory, sales tracking, customer-facing catalog, and GitHub Pages publishing.

## Architecture

```
ARV-Shop-Manager/
├── shop-manager/          # Flask backend + Manager UI (:8080)
│   ├── backend/          # Flask API, SQLite DB, image processing
│   │   ├── app.py        # Main Flask application
│   │   ├── shop.db       # SQLite database (created on first run)
│   │   ├── templates/    # Manager HTML UI
│   │   └── static/       # CSS, JS, service worker
│   └── scripts/          # Auto-backup to Google Drive
│
├── customer-view/        # Customer catalog + Auth + Cart
│   ├── manager/          # View Manager UI (:3001) - publish products
│   ├── site/             # Customer-facing site (:3000)
│   │   ├── index.html    # Main catalog page
│   │   ├── server.js     # Node.js server with auth/cart API
│   │   └── sw.js         # Service worker
│   ├── scripts/
│   │   ├── import.py     # Sync shop-manager → customer-view DB
│   │   └── generate.py   # Generate static site from customer-view DB
│   ├── db/               # customer-view.db (created by import.py)
│   └── images/           # Product images
│
└── git-pages/            # GitHub Pages deployment (gh-pages branch)
    ├── public/           # Static site (copy to gh-pages branch)
    │   ├── index.html    # Catalog page
    │   ├── app.js        # Frontend JavaScript
    │   ├── styles.css    # Dark theme styles
    │   ├── sw.js         # Service worker
    │   └── data/         # products.json, categories.json, settings.json
    ├── server.js         # Production Node.js server
    └── scripts/          # Catalog generation scripts
```

## Quick Start

### 1. Shop Manager (Admin)

```bash
cd shop-manager/backend
python3 app.py
# Open http://localhost:8080 (PIN: 1234)
```

### 2. Customer View (Catalog + Auth)

```bash
# Terminal 1: Import products from shop-manager
cd customer-view/scripts
python3 import.py /root/shop-manager/backend/shop.db

# Terminal 2: Generate static site
python3 generate.py

# Terminal 3: Start customer site
cd customer-view/site
node server.js
# Open http://localhost:3000
```

### 3. View Manager (Publish products)

```bash
cd customer-view/manager
node server.js
# Open http://localhost:3001
```

### 4. Publish to GitHub Pages

```bash
cd customer-view/scripts
python3 import.py /root/shop-manager/backend/shop.db
python3 generate.py

# Copy to Cinema123BW repo (gh-pages branch)
cd /root/Cinema123BW-Customer-Catalog
git checkout gh-pages
cp -r /root/customer-view/site/* public/
git add -A && git commit -m "Update catalog"
git push origin gh-pages
```

## Features

- **Inventory Management**: Add, edit, delete products with images
- **Sales Tracking**: Record sales, auto-reduce stock
- **Stock Status**: Out-of-stock products shown with badge on catalog
- **Google Drive Backup**: Auto-backup shop DB to Drive
- **Customer Catalog**: Searchable product catalog with cart
- **WhatsApp Checkout**: Customers enquire via WhatsApp
- **Guest/Auth Users**: Device fingerprinting + optional signup
- **Service Worker**: Offline support + hard refresh caching
- **GitHub Pages**: Free static hosting for customer catalog

## Database

- `shop-manager/backend/shop.db` — Main inventory DB (items, daily_sales)
- `customer-view/db/customer-view.db` — Customer-facing catalog (products, users, cart)

## Stock Status Logic

When a product's `quantity` reaches 0 in Shop Manager:
1. `import.py` sets `stock_status = 'out_of_stock'` in customer-view DB
2. `generate.py` includes it in `products.json`
3. Frontend shows "Out of Stock" badge, dims the card, hides Add to Cart/WhatsApp

## Tech Stack

- **Backend**: Python Flask (Shop Manager), Node.js (Customer View)
- **Database**: SQLite (both)
- **Frontend**: Vanilla JS, dark theme CSS
- **Hosting**: GitHub Pages (static catalog)
- **Storage**: Google Drive (backups)
