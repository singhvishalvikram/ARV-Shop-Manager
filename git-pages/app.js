// Cinema123BW Customer Catalog - Modern Frontend
(function() {
  'use strict';

  let allProducts = [];
  let viewConfig = {};
  let cart = [];
  let currentView = 'grid';

  async function init() {
    document.getElementById('currentYear').textContent = new Date().getFullYear();
    await loadViewConfig();
    await loadCatalog();
    renderCategories();
    renderProducts();
    setupEventListeners();
    setupOfflineDetection();
    startVersionPolling();
  }

  async function loadViewConfig() {
    try {
      const res = await fetch('data/view-config.json?v=' + Date.now());
      viewConfig = await res.json();
      console.log('[Catalog] View config loaded:', Object.keys(viewConfig).length, 'keys');
    } catch (e) { console.log('[Catalog] View config failed:', e); viewConfig = {}; }
    applyViewConfig();
  }

  function applyViewConfig() {
    if (!viewConfig || Object.keys(viewConfig).length === 0) return;

    if (viewConfig.app_title) {
      document.getElementById('appTitle').textContent = viewConfig.app_title;
      document.title = viewConfig.app_title;
    }
    if (viewConfig.app_subtitle) {
      document.getElementById('appSubtitle').textContent = viewConfig.app_subtitle;
    }
    if (viewConfig.theme_color) {
      document.querySelector('meta[name="theme-color"]').setAttribute('content', viewConfig.theme_color);
      document.documentElement.style.setProperty('--brand', viewConfig.theme_color);
    }
    if (viewConfig.shop_location) {
      const el = document.getElementById('shopLocation');
      el.textContent = '📍 ' + viewConfig.shop_location;
      el.style.display = 'block';
    }
    if (viewConfig.show_search === false) {
      document.querySelector('.search-wrap').style.display = 'none';
    }
    if (viewConfig.show_category_filter === false) {
      document.getElementById('categoryFilter').style.display = 'none';
    }
    if (viewConfig.products_per_row) {
      const grid = document.getElementById('productGrid');
      grid.style.gridTemplateColumns = `repeat(${viewConfig.products_per_row}, 1fr)`;
    }
    if (viewConfig.custom_css) {
      const style = document.createElement('style');
      style.textContent = viewConfig.custom_css;
      document.head.appendChild(style);
    }
  }

  async function loadCatalog() {
    try {
      const res = await fetch('data/products.json?v=' + Date.now());
      allProducts = await res.json();
      console.log('[Catalog] Products loaded:', allProducts.length);
    } catch (e) {
      console.log('[Catalog] First fetch failed, retrying...', e);
      try {
        allProducts = await (await fetch('data/products.json?v=' + Date.now())).json();
        console.log('[Catalog] Products loaded (retry):', allProducts.length);
      } catch (e2) {
        console.log('[Catalog] Products fetch failed completely:', e2);
        document.getElementById('productGrid').innerHTML = '';
        document.getElementById('emptyState').classList.remove('hidden');
      }
    }
  }

  function renderCategories() {
    const cats = [...new Set(allProducts.map(p => p.type).filter(Boolean))].sort();
    const container = document.getElementById('categoryFilter');
    if (cats.length <= 1) { container.innerHTML = ''; return; }

    let html = '<button class="category-pill active" data-cat="">All</button>';
    cats.forEach(cat => {
      html += `<button class="category-pill" data-cat="${escapeAttr(cat)}">${escapeHtml(cat)}</button>`;
    });
    container.innerHTML = html;
  }

  let currentSearch = '';
  let currentCategory = '';

  function getFilteredProducts() {
    let products = allProducts;

    // Hide products from view manager
    const hidden = viewConfig.hidden_products || [];
    if (hidden.length > 0) {
      products = products.filter(p => !hidden.includes(p.id));
    }

    // Category filter from view manager
    const allowedCats = viewConfig.categories || [];
    if (allowedCats.length > 0) {
      products = products.filter(p => allowedCats.includes(p.type));
    }

    // Search
    if (currentSearch) {
      const q = currentSearch.toLowerCase();
      products = products.filter(p =>
        p.name.toLowerCase().includes(q) ||
        (p.description || '').toLowerCase().includes(q) ||
        (p.type || '').toLowerCase().includes(q)
      );
    }

    // Category pill filter
    if (currentCategory) {
      products = products.filter(p => p.type === currentCategory);
    }

    // Max products
    if (viewConfig.max_products && viewConfig.max_products > 0) {
      products = products.slice(0, viewConfig.max_products);
    }

    return products;
  }

  function renderProducts() {
    const filtered = getFilteredProducts();
    const grid = document.getElementById('productGrid');
    const empty = document.getElementById('emptyState');
    const count = document.getElementById('productCount');

    console.log('[Catalog] Rendering', filtered.length, 'of', allProducts.length, 'products');
    count.textContent = filtered.length + ' product' + (filtered.length !== 1 ? 's' : '');

    if (filtered.length === 0) {
      grid.innerHTML = '';
      empty.classList.remove('hidden');
      return;
    }

    empty.classList.add('hidden');
    const sym = viewConfig.currency_symbol || '₹';
    const showDesc = viewConfig.show_description !== false;
    const showMrp = viewConfig.show_mrp !== false;
    const showDiscount = viewConfig.show_discount_badges !== false;
    const showImages = viewConfig.show_images !== false;
    const showLocation = viewConfig.show_location !== false;

    grid.innerHTML = filtered.map(function(p, i) {
      const imgSrc = p.image_url && p.image_url.indexOf('data:') !== 0
        ? 'images/' + p.image_url.split('/').pop()
        : p.image_url;
      const imgHtml = showImages
        ? (imgSrc
          ? '<img src="' + escapeAttr(imgSrc) + '" alt="' + escapeHtml(p.name) + '" loading="lazy" onerror="this.parentElement.innerHTML=\'<div class=no-image><svg viewBox=\'0 0 24 24\' fill=\'none\' stroke=\'currentColor\' stroke-width=\'1.5\'><path d=\'M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z\'/></svg></div>\'">'
          : '<div class="no-image"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"/></svg></div>')
        : '';
      const badge = showDiscount && p.discount_percent > 0
        ? '<span class="discount-badge">' + Math.round(p.discount_percent) + '% OFF</span>'
        : '';
      const oosBadge = p.stock_status === 'out_of_stock'
        ? '<span class="out-of-stock-badge">Out of Stock</span>'
        : '';
      const oosClass = p.stock_status === 'out_of_stock' ? ' out-of-stock' : '';
      const mrpHtml = showMrp && p.mrp > p.price
        ? '<span class="price-mrp">' + sym + p.mrp.toLocaleString('en-IN') + '</span>'
        : '';
      const locHtml = showLocation && p.location
        ? '<span class="product-location-tag">📍 ' + escapeHtml(p.location) + '</span>'
        : '';

      return '<div class="product-card' + oosClass + '" data-id="' + p.id + '" style="animation-delay:' + (i * 0.03) + 's">' +
        '<div class="product-image-wrap">' + imgHtml + badge + oosBadge + '</div>' +
        '<div class="product-info">' +
          '<div class="product-category">' + escapeHtml(p.type) + '</div>' +
          '<div class="product-name">' + escapeHtml(p.name) + '</div>' +
          '<div class="product-price"><span class="price-current">' + sym + p.price.toLocaleString('en-IN') + '</span>' + mrpHtml + '</div>' +
          locHtml +
        '</div>' +
      '</div>';
    }).join('');
  }

  function showProductDetail(product) {
    const sym = viewConfig.currency_symbol || '₹';
    const whatsapp = viewConfig.whatsapp_number || '919876543210';
    const locationStr = product.location ? '\nLocation: ' + product.location : '';
    const whatsappMsg = encodeURIComponent(
      'Hello,\n\nI am interested in the following product:\n\nProduct: ' + product.name + '\nPrice: ' + sym + product.price + locationStr + '\n\nPlease provide more details.'
    );
    const whatsappLink = 'https://wa.me/' + whatsapp + '?text=' + whatsappMsg;

    const imgSrc = product.image_url && product.image_url.indexOf('data:') !== 0
      ? 'images/' + product.image_url.split('/').pop()
      : product.image_url;

    document.getElementById('modalImage').src = imgSrc || '';
    document.getElementById('modalCategory').textContent = product.type || '';
    document.getElementById('modalTitle').textContent = product.name;
    document.getElementById('modalDesc').textContent = product.description || '';
    document.getElementById('modalDesc').style.display = product.description ? 'block' : 'none';
    document.getElementById('modalLocation').textContent = product.location ? '📍 ' + product.location : '';
    document.getElementById('modalLocation').style.display = product.location ? 'flex' : 'none';
    document.getElementById('modalPrice').textContent = sym + product.price.toLocaleString('en-IN');
    document.getElementById('modalMrp').textContent = product.mrp > product.price ? sym + product.mrp.toLocaleString('en-IN') : '';
    document.getElementById('modalMrp').style.display = product.mrp > product.price ? 'inline' : 'none';
    document.getElementById('modalDiscount').textContent = product.discount_percent > 0 ? Math.round(product.discount_percent) + '% OFF' : '';
    document.getElementById('modalDiscount').style.display = product.discount_percent > 0 ? 'inline-block' : 'none';
    // Out of stock handling
    const oosEl = document.getElementById('modalOutOfStock');
    const waBtn = document.getElementById('whatsappBtn');
    if (product.stock_status === 'out_of_stock') {
      if (oosEl) oosEl.style.display = 'inline-block';
      if (waBtn) { waBtn.style.display = 'none'; }
    } else {
      if (oosEl) oosEl.style.display = 'none';
      if (waBtn) { waBtn.style.display = 'inline-flex'; }
    }
    document.getElementById('whatsappBtn').href = whatsappLink;
    document.getElementById('productModal').classList.add('show');
    document.body.style.overflow = 'hidden';
  }

  function hideModal() {
    document.getElementById('productModal').classList.remove('show');
    document.body.style.overflow = '';
  }

  function setupEventListeners() {
    // Search
    let debounceTimer;
    const searchInput = document.getElementById('searchInput');
    const clearBtn = document.getElementById('clearSearch');

    searchInput.addEventListener('input', () => {
      clearTimeout(debounceTimer);
      debounceTimer = setTimeout(() => {
        currentSearch = searchInput.value.trim();
        clearBtn.classList.toggle('show', !!currentSearch);
        renderProducts();
      }, 200);
    });

    clearBtn.addEventListener('click', () => {
      searchInput.value = '';
      currentSearch = '';
      clearBtn.classList.remove('show');
      renderProducts();
    });

    // Category pills
    document.getElementById('categoryFilter').addEventListener('click', (e) => {
      const pill = e.target.closest('.category-pill');
      if (!pill) return;
      document.querySelectorAll('.category-pill').forEach(p => p.classList.remove('active'));
      pill.classList.add('active');
      currentCategory = pill.dataset.cat || '';
      renderProducts();
    });

    // Product card click
    document.getElementById('productGrid').addEventListener('click', (e) => {
      const card = e.target.closest('.product-card');
      if (!card) return;
      const id = parseInt(card.dataset.id);
      const product = allProducts.find(p => p.id === id);
      if (product) showProductDetail(product);
    });

    // Modal close
    document.getElementById('modalClose').addEventListener('click', hideModal);
    document.getElementById('productModal').addEventListener('click', (e) => {
      if (e.target === document.getElementById('productModal')) hideModal();
    });
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') hideModal(); });

    // Share
    document.getElementById('shareBtn').addEventListener('click', async () => {
      const url = window.location.href;
      if (navigator.share) {
        try { await navigator.share({ title: document.title, url }); } catch (e) {}
      } else {
        try {
          await navigator.clipboard.writeText(url);
          alert('Link copied!');
        } catch (e) { prompt('Copy:', url); }
      }
    });

    // View toggle
    document.querySelectorAll('.view-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('.view-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        currentView = btn.dataset.view;
        const grid = document.getElementById('productGrid');
        grid.classList.toggle('list-view', currentView === 'list');
      });
    });
  }

  function setupOfflineDetection() {
    function update() {
      const existing = document.querySelector('.offline-banner');
      if (!navigator.onLine) {
        if (!existing) {
          const banner = document.createElement('div');
          banner.className = 'offline-banner';
          banner.textContent = 'You are offline. Showing cached catalog.';
          document.body.insertBefore(banner, document.body.firstChild);
        }
      } else if (existing) { existing.remove(); }
    }
    window.addEventListener('online', update);
    window.addEventListener('offline', update);
    update();
  }

  function startVersionPolling() {
    setInterval(async () => {
      try {
        const res = await fetch('api/version?v=' + Date.now());
        const data = await res.json();
        if (data.asset_version && window._assetVersion && data.asset_version !== window._assetVersion) {
          location.reload();
        }
        window._assetVersion = data.asset_version;
      } catch (e) {}
    }, 10000);
  }

  function escapeHtml(str) {
    const d = document.createElement('div');
    d.textContent = str;
    return d.innerHTML;
  }

  function escapeAttr(str) {
    return str.replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  init();
})();
