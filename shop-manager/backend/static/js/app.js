// Global state
let appState = {
    items: [],
    sales: [],
    cameraStream: null,
    capturedImageBase64: null,
    isOnline: navigator.onLine,
    currentTab: 'dashboard',
    selectedSaleItem: null,
    searchTimeout: null,
    editCameraStream: null,
    editCapturedImageBase64: null,
    editImageRemoved: false
};

// ========== AUTH / LOGIN ==========
// Server-side authentication via the shared service layer (window.Auth, set by
// api-globals.js). Replaces the old client-side PIN, which was no real gate.

function showLoginScreen() {
    document.getElementById('mainApp').style.display = 'none';
    document.getElementById('lockScreen').style.display = 'flex';
}

function showApp() {
    document.getElementById('lockScreen').style.display = 'none';
    document.getElementById('mainApp').style.display = 'block';
}

async function doLogin(e) {
    if (e) e.preventDefault();
    const errEl = document.getElementById('lockError');
    const btn = document.getElementById('loginSubmitBtn');
    const phone = document.getElementById('loginPhone').value.trim();
    const password = document.getElementById('loginPassword').value;
    errEl.textContent = '';

    if (!phone || !password) {
        errEl.textContent = 'Enter your phone and password.';
        return;
    }
    btn.disabled = true;
    btn.textContent = 'Signing in…';
    try {
        if (window.__authMode === 'signup') {
            await window.Auth.signup({ phone, password, name: '' });
        } else {
            await window.Auth.login({ phone, password });
        }
        showApp();
        await loadInitialData();
    } catch (err) {
        errEl.textContent = (err && err.code === 'UNAUTHORIZED')
            ? 'Invalid phone or password.'
            : (err && err.message) || 'Sign-in failed. Try again.';
    } finally {
        btn.disabled = false;
        btn.textContent = window.__authMode === 'signup' ? 'Create account' : 'Sign In';
    }
}

function startGoogleLogin() {
    window.location.href = window.Auth.googleLoginUrl();
}

function toggleSignup(e) {
    if (e) e.preventDefault();
    window.__authMode = window.__authMode === 'signup' ? 'login' : 'signup';
    const isSignup = window.__authMode === 'signup';
    document.getElementById('loginSubmitBtn').textContent = isSignup ? 'Create account' : 'Sign In';
    document.querySelector('#lockScreen .lock-subtitle').textContent =
        isSignup ? 'Create an owner account' : 'Sign in to continue';
    document.getElementById('lockError').textContent = '';
}

async function logout(e) {
    if (e) e.preventDefault();
    closeDrawer();
    try { await window.Auth.logout(); } catch (err) { /* token cleared regardless */ }
    showLoginScreen();
}

/** True if a stored session token is still valid (server-checked). */
async function hasValidSession() {
    if (!window.Auth || !window.Auth.getToken()) return false;
    try {
        await window.Auth.me();
        return true;
    } catch (err) {
        return false;
    }
}

// ========== APP INITIALIZATION ==========
document.addEventListener('DOMContentLoaded', async () => {
    console.log('🚀 App initializing...');

    // Initialize IndexedDB first
    try {
        await LocalDB.openDB();
        console.log('📦 IndexedDB initialized');
    } catch (err) {
        console.error('IndexedDB init failed:', err);
    }

    setupEventListeners();
    setupOnlineOfflineListeners();
    checkOnlineStatus();

    // Apply white-label branding from shop settings (public; works pre-login).
    try { await window.Branding.loadBranding(); } catch (e) { /* defaults stand */ }

    // Gate on a real server session. A Google redirect token (if any) was already
    // captured by api-globals.js before this ran.
    if (await hasValidSession()) {
        showApp();
        await loadInitialData();
    } else {
        showLoginScreen();
        // Preload local cache so the app is ready the moment sign-in succeeds.
        try {
            await LocalDB.getLocalItems();
            await LocalDB.getLocalSales();
        } catch (e) {
            console.warn('Preload failed:', e);
        }
    }
});

async function loadInitialData() {
    // Always fetch from server first — server is the source of truth
    try {
        const serverItems = await window.API.items.listItems();
        appState.items = serverItems;
        renderItems(appState.items);
        // Save to IndexedDB as cache (don't block on it)
        syncServerItemsToLocal(serverItems).catch(() => {});
    } catch (error) {
        // Server unreachable — fallback to IndexedDB
        console.error('Server fetch failed, using local:', error);
        await loadItemsFromLocal();
    }

    // Load sales
    try {
        appState.sales = await window.API.sales.listSales();
        renderSales(appState.sales);
    } catch (e) {
        await loadSalesFromLocal();
    }
    // Also load dashboard
    await loadDashboard();
}

// Setup event listeners
function setupEventListeners() {
    // Login screen
    document.getElementById('loginForm')?.addEventListener('submit', doLogin);
    document.getElementById('googleLoginBtn')?.addEventListener('click', startGoogleLogin);
    document.getElementById('showSignupLink')?.addEventListener('click', toggleSignup);

    // Search functionality
    document.getElementById('searchInput')?.addEventListener('input', (e) => {
        filterItems(e.target.value);
    });

    // Camera controls
    document.getElementById('startCameraBtn')?.addEventListener('click', startCamera);
    document.getElementById('captureCameraBtn')?.addEventListener('click', captureImage);
    document.getElementById('stopCameraBtn')?.addEventListener('click', stopCamera);
    document.getElementById('retakeBtn')?.addEventListener('click', retakePhoto);
    document.getElementById('useImageBtn')?.addEventListener('click', useImage);

    // Form submission
    document.getElementById('itemForm')?.addEventListener('submit', addItem);

    // Edit form submission
    document.getElementById('editItemForm')?.addEventListener('submit', saveEditItem);

    // Modal close
    document.querySelector('#itemModal .modal-close')?.addEventListener('click', closeItemModal);
    document.getElementById('itemModal')?.addEventListener('click', (e) => {
        if (e.target.id === 'itemModal') closeItemModal();
    });

    // Drawer overlay
    document.getElementById('drawerOverlay')?.addEventListener('click', closeDrawer);

    // Install PWA
    window.addEventListener('beforeinstallprompt', (e) => {
        e.preventDefault();
        window.deferredPrompt = e;
        document.getElementById('installBtn').style.display = 'block';
        document.getElementById('installBtn').addEventListener('click', installPWA);
    });

    // Sale search input
    document.getElementById('saleSearchInput')?.addEventListener('input', (e) => {
        clearTimeout(appState.searchTimeout);
        const query = e.target.value.trim();
        if (query.length < 1) {
            hideSaleSearchResults();
            return;
        }
        appState.searchTimeout = setTimeout(() => searchSaleItems(query), 200);
    });

    document.getElementById('saleSearchInput')?.addEventListener('blur', () => {
        setTimeout(hideSaleSearchResults, 200);
    });

    document.getElementById('saleSearchInput')?.addEventListener('focus', () => {
        const query = document.getElementById('saleSearchInput').value.trim();
        if (query.length >= 1) {
            searchSaleItems(query);
        }
    });

    // Click outside to close search results
    document.addEventListener('click', (e) => {
        if (!e.target.closest('#saleSearchInput') && !e.target.closest('#saleSearchResults')) {
            hideSaleSearchResults();
        }
    });
}

function setupOnlineOfflineListeners() {
    window.addEventListener('online', () => {
        appState.isOnline = true;
        checkOnlineStatus();
        syncOfflineData();
    });

    window.addEventListener('offline', () => {
        appState.isOnline = false;
        checkOnlineStatus();
    });
}

function checkOnlineStatus() {
    const badge = document.getElementById('onlineStatus');
    if (appState.isOnline) {
        badge.textContent = '● Online';
        badge.classList.remove('offline');
    } else {
        badge.textContent = '● Offline';
        badge.classList.add('offline');
    }
}

// ========== DRAWER NAVIGATION ==========
function openDrawer() {
    document.getElementById('navigationDrawer').classList.add('open');
    document.getElementById('drawerOverlay').classList.add('open');
}

function closeDrawer() {
    document.getElementById('navigationDrawer').classList.remove('open');
    document.getElementById('drawerOverlay').classList.remove('open');
}

function selectDrawerItem(tabName, event) {
    event.preventDefault();
    closeDrawer();
    switchTab(tabName);
}

// ========== TAB SWITCHING ==========
function switchTab(tabName) {
    document.querySelectorAll('.tab-content').forEach(tab => tab.classList.remove('active'));
    const tab = document.getElementById(tabName);
    if (tab) {
        tab.classList.add('active');
    }
    appState.currentTab = tabName;

    if (tabName === 'items') loadItems();
    if (tabName === 'sales') loadSales();
    if (tabName === 'dashboard') loadDashboard();
    if (tabName === 'camera') resetCameraForm();
}

// ========== DASHBOARD ==========
async function loadDashboard() {
    // Try server first for latest data
    try {
        const data = await window.API.dashboard.getDashboard();
        renderDashboard(data);
        saveToCache('dashboard', data);
    } catch (error) {
        console.error('Dashboard server error:', error);
        // Fallback to local DB
        await loadDashboardFromLocal();
    }
}

async function loadDashboardFromLocal() {
    try {
        const stats = await LocalDB.getDatabaseStats();
        renderDashboard({
            total_items: stats.total_items,
            total_quantity: stats.total_stock,
            total_stock_value: stats.total_stock_value,
            total_stock_cost: stats.total_stock_cost || 0,
            total_stock_mrp: stats.total_stock_mrp || 0,
            today_revenue: stats.today_revenue,
            recent_items: (await LocalDB.getLocalItems()).slice(0, 10)
        });
    } catch (error) {
        console.error('Local dashboard error:', error);
        // Last resort: cache
        const cached = loadFromCache('dashboard');
        if (cached) renderDashboard(cached);
    }
}

function renderDashboard(data) {
    document.getElementById('totalItems').textContent = data.total_items || 0;
    document.getElementById('totalStock').textContent = data.total_quantity || 0;
    document.getElementById('stockCost').textContent = '₹' + formatNumber(data.total_stock_cost || 0);
    document.getElementById('stockMrp').textContent = '₹' + formatNumber(data.total_stock_mrp || 0);
    document.getElementById('todayRevenue').textContent = '₹' + formatNumber(data.today_revenue || 0);

    const lowStockList = document.getElementById('lowStockList');
    const lowStock = (data.recent_items || []).filter(item => item.quantity <= 10);
    lowStockList.innerHTML = lowStock.map(item => `
        <li class="${item.quantity === 0 ? 'zero' : 'low'}">
            <span>${item.name}</span>
            <span class="qty">${item.quantity}</span>
        </li>
    `).join('');
}

// ========== ITEMS MANAGEMENT ==========
async function loadItems() {
    // Server load (source of truth), with IndexedDB fallback when offline.
    try {
        appState.items = await window.API.items.listItems();
        renderItems(appState.items);
        saveToCache('items', appState.items);
        // Also sync to IndexedDB
        await syncServerItemsToLocal(appState.items);
    } catch (error) {
        console.error('❌ Items server error:', error);
        await loadItemsFromLocal();
    }
}

async function loadItemsFromLocal() {
    try {
        const localItems = await LocalDB.getLocalItems();
        appState.items = localItems.map(item => ({
            ...item,
            id: item.server_id || item.local_id  // Use server_id as display ID if available
        }));
        console.log('📦 Loaded items from local DB:', appState.items.length);
        renderItems(appState.items);
    } catch (error) {
        console.error('Local items error:', error);
        const cached = loadFromCache('items');
        if (cached) {
            appState.items = cached;
            renderItems(appState.items);
        }
    }
}

async function syncServerItemsToLocal(serverItems) {
    try {
        for (const serverItem of serverItems) {
            const existing = await LocalDB.getLocalItemByServerId(serverItem.id);
            if (existing) {
                // Update if server version is newer
                const serverDate = new Date(serverItem.updated_at || 0);
                const localDate = new Date(existing.updated_at || 0);
                if (serverDate >= localDate) {
                    await LocalDB.saveLocalItem({
                        ...existing,
                        ...serverItem,
                        local_id: existing.local_id,
                        server_id: serverItem.id,
                        synced: true,
                        synced_at: new Date().toISOString()
                    });
                }
            } else {
                // New item from server
                await LocalDB.saveLocalItem({
                    ...serverItem,
                    server_id: serverItem.id,
                    synced: true,
                    synced_at: new Date().toISOString()
                });
            }
        }
    } catch (err) {
        console.error('Server->Local sync error:', err);
    }
}

async function syncWithServer() {
    // Fetch from server and update local DB
    try {
        const serverItems = await window.API.items.listItems();
        await syncServerItemsToLocal(serverItems);

        // Refresh display
        await loadItemsFromLocal();
        await loadDashboardFromLocal();
    } catch (err) {
        console.error('Server sync failed:', err);
    }
}

function renderItems(items) {
    const container = document.getElementById('itemsList');
    if (!container) return;

    if (!items || items.length === 0) {
        container.innerHTML = '<div class="empty-state">No items yet. Add one from the Camera tab!</div>';
        return;
    }

    container.innerHTML = items.map(item => `
        <div class="item-card" onclick="openItemDetail(${item.id})">
            <div class="item-image">
                ${item.image_url ? `<img src="${item.image_url}" alt="${item.name}" onerror="this.innerHTML='📦'">` : '<div style="font-size:40px;">📦</div>'}
            </div>
            <div class="item-info">
                <div class="item-name">${item.name}</div>
                <div class="item-price">₹${formatNumber(item.price)} <span style="font-size:11px;color:#999;">MRP: ₹${formatNumber(item.mrp || 0)}</span></div>
                <div class="qty-badge ${getQtyClass(item.quantity)}">
                    ${item.quantity} in stock
                </div>
            </div>
        </div>
    `).join('');
}

function filterItems(query) {
    const searchWords = query.toLowerCase().trim().split(/\s+/).filter(w => w.length > 0);
    
    if (searchWords.length === 0) {
        renderItems(appState.items);
        return;
    }

    const filtered = appState.items.filter(item => {
        const name = (item.name || '').toLowerCase();
        const type = (item.type || '').toLowerCase();
        const description = (item.description || '').toLowerCase();
        const location = (item.location || '').toLowerCase();
        const searchableText = name + ' ' + type + ' ' + description + ' ' + location;
        
        // Match if ANY search word appears in ANY field
        return searchWords.some(word => searchableText.includes(word));
    });
    
    renderItems(filtered);
}

function getQtyClass(quantity) {
    if (quantity === 0) return 'qty-zero';
    if (quantity <= 10) return 'qty-low';
    return 'qty-ok';
}

// ========== ITEM DETAIL MODAL ==========
function openItemDetail(itemId) {
    const item = appState.items.find(i => i.id === itemId);
    if (!item) return;

    const details = document.getElementById('itemDetails');
    details.innerHTML = `
        <div style="text-align: center;">
            ${item.image_url ? `<img src="${item.image_url}" style="max-width: 100%; border-radius: 8px; margin-bottom: 15px;" onerror="this.style.display='none'">` : ''}
            <div style="font-size: 60px; margin-bottom: 15px;">📦</div>
        </div>
        <h2>${item.name}</h2>
        <p><strong>Type:</strong> ${item.type || 'N/A'}</p>
        <p><strong>Description:</strong> ${item.description || 'N/A'}</p>
        <p><strong>Selling Price:</strong> ₹${formatNumber(item.price)}</p>
        <p><strong>MRP:</strong> ₹${formatNumber(item.mrp || 0)}</p>
        <p><strong>Purchase Cost:</strong> ₹${formatNumber(item.purchase_cost || 0)}</p>
        <p><strong>Profit/Unit:</strong> ₹${formatNumber((item.price || 0) - (item.purchase_cost || 0))}</p>
        <p><strong>Quantity:</strong> <span class="qty-badge ${getQtyClass(item.quantity)}">${item.quantity}</span></p>
        <p><strong>Location:</strong> ${item.location || 'N/A'}</p>
        <div style="margin-top: 20px; display:flex;gap:10px;">
            <button onclick="openEditItem(${item.id})" class="btn-primary" style="flex:1;">✏️ Edit Item</button>
            <button onclick="deleteItem(${item.id})" class="btn-danger" style="flex:1;">Delete Item</button>
        </div>
    `;

    document.getElementById('itemModal').style.display = 'flex';
}

function closeItemModal() {
    document.getElementById('itemModal').style.display = 'none';
}

// ========== EDIT ITEM ==========
function openEditItem(itemId) {
    const item = appState.items.find(i => i.id === itemId);
    if (!item) return;

    // Close the detail modal first
    closeItemModal();

    // Fill the edit form
    document.getElementById('editItemId').value = item.id;
    document.getElementById('editItemName').value = item.name || '';
    document.getElementById('editItemType').value = item.type || '';
    document.getElementById('editItemDescription').value = item.description || '';
    document.getElementById('editItemPrice').value = item.price || 0;
    document.getElementById('editItemMrp').value = item.mrp || 0;
    document.getElementById('editItemPurchaseCost').value = item.purchase_cost || 0;
    document.getElementById('editItemQuantity').value = item.quantity || 0;
    document.getElementById('editItemLocation').value = item.location || '';
    document.getElementById('editImageBase64').value = '';

    // Reset edit image state
    appState.editCapturedImageBase64 = null;
    appState.editImageRemoved = false;

    // Show current image if exists
    const currentImg = document.getElementById('editCurrentImage');
    const noImage = document.getElementById('editNoImage');
    const removeBtn = document.getElementById('editRemoveImageBtn');
    if (item.image_url && item.image_url !== '') {
        currentImg.src = item.image_url;
        currentImg.style.display = 'inline';
        currentImg.onerror = () => { currentImg.style.display = 'none'; noImage.textContent = 'Image failed to load'; noImage.style.display = 'block'; };
        noImage.style.display = 'none';
        removeBtn.style.display = 'inline-block';
    } else {
        currentImg.style.display = 'none';
        currentImg.src = '';
        noImage.textContent = 'No image';
        noImage.style.display = 'block';
        removeBtn.style.display = 'none';
    }

    // Reset camera UI
    editResetCameraUI();

    // Show edit modal
    document.getElementById('editItemModal').style.display = 'flex';
}

function closeEditModal() {
    // Stop any active camera
    if (appState.editCameraStream) {
        appState.editCameraStream.getTracks().forEach(track => track.stop());
        appState.editCameraStream = null;
    }
    document.getElementById('editItemModal').style.display = 'none';
}

// ========== EDIT CAMERA FUNCTIONS ==========
function editResetCameraUI() {
    document.getElementById('editCameraPreviewContainer').style.display = 'none';
    document.getElementById('editCapturedPreviewContainer').style.display = 'none';
    document.getElementById('editStartCameraBtn').style.display = 'inline-block';
    document.getElementById('editRemoveImageBtn').style.display = document.getElementById('editCurrentImage').src ? 'inline-block' : 'none';
    if (appState.editCameraStream) {
        appState.editCameraStream.getTracks().forEach(track => track.stop());
        appState.editCameraStream = null;
    }
    appState.editCapturedImageBase64 = null;
}

async function editStartCamera() {
    try {
        const stream = await navigator.mediaDevices.getUserMedia({
            video: { facingMode: 'environment' }
        });
        const video = document.getElementById('editCameraPreview');
        video.srcObject = stream;
        appState.editCameraStream = stream;

        document.getElementById('editCameraPreviewContainer').style.display = 'block';
        document.getElementById('editCapturedPreviewContainer').style.display = 'none';
        document.getElementById('editStartCameraBtn').style.display = 'none';
    } catch (error) {
        alert('Camera permission denied or not available');
        console.error('Edit camera error:', error);
    }
}

function editCaptureImage() {
    const video = document.getElementById('editCameraPreview');
    if (!video.videoWidth) return;

    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(video, 0, 0);

    appState.editCapturedImageBase64 = canvas.toDataURL('image/jpeg', 0.8);
    document.getElementById('editCapturedImage').src = appState.editCapturedImageBase64;

    // Stop camera
    if (appState.editCameraStream) {
        appState.editCameraStream.getTracks().forEach(track => track.stop());
        appState.editCameraStream = null;
    }

    document.getElementById('editCameraPreviewContainer').style.display = 'none';
    document.getElementById('editCapturedPreviewContainer').style.display = 'block';
}

function editStopCamera() {
    if (appState.editCameraStream) {
        appState.editCameraStream.getTracks().forEach(track => track.stop());
        appState.editCameraStream = null;
    }
    document.getElementById('editCameraPreviewContainer').style.display = 'none';
    document.getElementById('editStartCameraBtn').style.display = 'inline-block';
}

function editRetakePhoto() {
    appState.editCapturedImageBase64 = null;
    document.getElementById('editCapturedPreviewContainer').style.display = 'none';
    editStartCamera();
}

function editUseImage() {
    if (appState.editCapturedImageBase64) {
        // Hide current image preview, show the captured one
        document.getElementById('editCurrentImage').src = appState.editCapturedImageBase64;
        document.getElementById('editCurrentImage').style.display = 'inline';
        document.getElementById('editNoImage').style.display = 'none';
        document.getElementById('editImageRemoved').value = '';
        appState.editImageRemoved = false;

        document.getElementById('editCapturedPreviewContainer').style.display = 'none';
        document.getElementById('editStartCameraBtn').style.display = 'inline-block';
        document.getElementById('editRemoveImageBtn').style.display = 'inline-block';

        // Set the hidden field
        document.getElementById('editImageBase64').value = appState.editCapturedImageBase64;
    }
}

function editRemoveImage() {
    appState.editImageRemoved = true;
    appState.editCapturedImageBase64 = null;
    document.getElementById('editCurrentImage').src = '';
    document.getElementById('editCurrentImage').style.display = 'none';
    document.getElementById('editNoImage').textContent = 'No image';
    document.getElementById('editNoImage').style.display = 'block';
    document.getElementById('editRemoveImageBtn').style.display = 'none';
    document.getElementById('editImageBase64').value = '';
    editResetCameraUI();
}

// ========== BACKUP & RESTORE ==========
async function showBackupModal(e) {
    if (e) e.preventDefault();
    closeDrawer();
    
    // Load stats
    const statsEl = document.getElementById('backupStats');
    statsEl.innerHTML = '<p style="color:#999;">Loading...</p>';
    
    try {
        const stats = await LocalDB.getDatabaseStats();
        statsEl.innerHTML = `
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;">
                <div class="dashboard-card" style="padding:10px;">
                    <h4 style="font-size:12px;">Items</h4>
                    <p class="big-number" style="font-size:24px;">${stats.total_items}</p>
                </div>
                <div class="dashboard-card" style="padding:10px;">
                    <h4 style="font-size:12px;">Total Stock</h4>
                    <p class="big-number" style="font-size:24px;">${stats.total_stock}</p>
                </div>
                <div class="dashboard-card" style="padding:10px;">
                    <h4 style="font-size:12px;">Sales Records</h4>
                    <p class="big-number" style="font-size:24px;">${stats.total_sales}</p>
                </div>
                <div class="dashboard-card" style="padding:10px;">
                    <h4 style="font-size:12px;">Stock Value</h4>
                    <p class="big-number" style="font-size:24px;">₹${formatNumber(stats.total_stock_value)}</p>
                </div>
            </div>
        `;
    } catch (err) {
        statsEl.innerHTML = '<p style="color:red;">Failed to load stats</p>';
    }
    
    document.getElementById('backupStatus').innerHTML = '';
    document.getElementById('backupModal').style.display = 'flex';
}

function closeBackupModal() {
    document.getElementById('backupModal').style.display = 'none';
}

async function doDownloadBackup() {
    try {
        const statusEl = document.getElementById('backupStatus');
        statusEl.innerHTML = '<p style="color:#2196F3;">⏳ Preparing backup...</p>';
        
        await LocalDB.downloadBackup();
        
        statusEl.innerHTML = '<p style="color:green;">✅ Backup downloaded! Check your Downloads folder.</p>';
        
        // Update last backup time
        await LocalDB.setSetting('last_backup', Date.now().toString());
    } catch (error) {
        console.error('Backup error:', error);
        document.getElementById('backupStatus').innerHTML = 
            '<p style="color:red;">❌ Backup failed: ' + error.message + '</p>';
    }
}

async function doRestoreBackup(event) {
    const file = event.target.files[0];
    if (!file) return;
    
    const statusEl = document.getElementById('backupStatus');
    statusEl.innerHTML = '<p style="color:#2196F3;">⏳ Restoring data...</p>';
    
    const clearExisting = confirm('Clear all existing data before importing? (Cancel = merge with existing)');
    
    try {
        const result = await LocalDB.importData(file, { clear_existing: clearExisting });
        
        statusEl.innerHTML = `<p style="color:green;">
            ✅ Restore complete!<br>
            Imported: ${result.imported_items} items, ${result.imported_sales} sales<br>
            Skipped: ${result.skipped_items} items, ${result.skipped_sales} sales<br>
            Total in database: ${result.total_items} items, ${result.total_sales} sales
        </p>`;
        
        // Refresh all views
        await loadItemsFromLocal();
        await loadSalesFromLocal();
        await loadDashboardFromLocal();
        
    } catch (error) {
        console.error('Restore error:', error);
        statusEl.innerHTML = '<p style="color:red;">❌ Restore failed: ' + error.message + '</p>';
    }
    
    // Reset file input
    event.target.value = '';
}

async function saveEditItem(e) {
    e.preventDefault();

    const itemId = parseInt(document.getElementById('editItemId').value);
    const name = document.getElementById('editItemName').value.trim();
    const type = document.getElementById('editItemType').value.trim();
    const description = document.getElementById('editItemDescription').value.trim();
    const price = parseFloat(document.getElementById('editItemPrice').value);
    const mrp = parseFloat(document.getElementById('editItemMrp').value) || 0;
    const purchase_cost = parseFloat(document.getElementById('editItemPurchaseCost').value) || 0;
    const quantity = parseInt(document.getElementById('editItemQuantity').value);
    const location = document.getElementById('editItemLocation').value.trim();

    // Client-side validation: name, type, price, quantity required
    if (!name) {
        alert('Item name is required');
        return;
    }
    if (!type) {
        alert('Item type is required');
        return;
    }
    if (isNaN(price) || price <= 0) {
        alert('Price must be a positive number');
        return;
    }
    if (isNaN(quantity) || quantity < 0) {
        alert('Quantity must be 0 or more');
        return;
    }

    const data = { name, type, description, price, mrp, purchase_cost, quantity, location };

    // Handle image: new capture, removed, or keep existing
    if (appState.editImageRemoved) {
        data.image_url = '';
    } else if (appState.editCapturedImageBase64) {
        data.image_base64 = appState.editCapturedImageBase64;
    }
    // If neither removed nor new capture, backend keeps existing image_url

    try {
        await window.API.items.updateItem(itemId, data);
        alert('✓ Item updated successfully!');
        closeEditModal();
        await loadItems();
        await loadDashboard();
    } catch (error) {
        console.error('Edit error:', error);
        alert('Error: ' + (error.message || 'Failed to update item'));
    }
}

// ========== ADD ITEM ==========
async function addItem(e) {
    e.preventDefault();

    const formData = new FormData(document.getElementById('itemForm'));
    const name = formData.get('name')?.trim();
    const type = formData.get('type')?.trim();
    const description = formData.get('description')?.trim();
    const price = parseFloat(formData.get('price'));
    const quantity = parseInt(formData.get('quantity'));
    const location = formData.get('location')?.trim();

    // Validation: name, type, price, quantity are mandatory
    if (!name) {
        alert('Item name is required');
        return;
    }
    if (!type) {
        alert('Item type is required');
        return;
    }
    if (isNaN(price) || price <= 0) {
        alert('Price must be a positive number');
        return;
    }
    if (isNaN(quantity) || quantity < 0) {
        alert('Quantity must be 0 or more');
        return;
    }

    const data = {
        name,
        type,
        description,
        price,
        quantity,
        location,
        image_base64: formData.get('image_base64') || null
    };

    try {
        await window.API.items.createItem(data);
        alert('✓ Item added successfully!');
        document.getElementById('itemForm').reset();
        document.getElementById('imageBase64').value = '';
        document.getElementById('imagePreview').style.display = 'none';
        document.getElementById('cameraPreview').style.display = 'block';
        document.getElementById('startCameraBtn').style.display = 'block';
        appState.capturedImageBase64 = null;

        await loadItems();
        await loadDashboard();
        switchTab('items');
    } catch (error) {
        console.error('Add item error:', error);
        if (!appState.isOnline) {
            saveOfflineAction('addItem', data);
            alert('Offline: Item will be added when online');
        } else {
            alert('Error: ' + (error.message || 'Failed to add item'));
        }
    }
}

// ========== DELETE ITEM ==========
async function deleteItem(itemId) {
    if (!confirm('Delete this item?')) return;

    try {
        await window.API.items.deleteItem(itemId);
        alert('Item deleted');
        closeItemModal();
        await loadItems();
        await loadDashboard();
    } catch (error) {
        console.error('Delete error:', error);
        alert('Error: ' + (error.message || 'Failed to delete item'));
    }
}

// ========== SALES MANAGEMENT ==========
async function loadSales() {
    try {
        appState.sales = await window.API.sales.listSales();
        renderSales(appState.sales);
        saveToCache('sales', appState.sales);
    } catch (error) {
        console.error('Sales error:', error);
        const cached = loadFromCache('sales');
        if (cached) renderSales(cached);
    }
}

function renderSales(sales) {
    const container = document.getElementById('salesList');
    if (!container) return;

    if (!sales || sales.length === 0) {
        container.innerHTML = '<li class="empty">No sales recorded today</li>';
        return;
    }

    container.innerHTML = sales.map(sale => {
        // Format the sale line
        const itemName = sale.item_name || 'Unknown';
        const quantity = sale.quantity_sold || sale.quantity || 0;
        const price = sale.sale_price || 0;
        const total = price * quantity;
        const notes = sale.description || '';
        
        let saleHTML = `
            <li class="sale-item">
                <span>${itemName}</span>
                <span>x${quantity} = ₹${formatNumber(total)}</span>
        `;
        
        // Add price per unit if different from item price (or always show for clarity)
        if (price > 0) {
            saleHTML += `<span class="sale-price-detail">@ ₹${formatNumber(price)}</span>`;
        }
        
        // Add notes if they exist
        if (notes && notes.trim() !== '') {
            saleHTML += `<span class="sale-notes">"${notes}"</span>`;
        }
        
        saleHTML += '</li>';
        return saleHTML;
    }).join('');
}

// ========== SALE SEARCH ==========
function searchSaleItems(query) {
    const resultsContainer = document.getElementById('saleSearchResults');
    if (!resultsContainer) return;

    if (!query || query.length < 1) {
        hideSaleSearchResults();
        return;
    }

    const searchWords = query.toLowerCase().trim().split(/\s+/).filter(w => w.length > 0);
    
    const matches = appState.items.filter(item => {
        const name = (item.name || '').toLowerCase();
        const type = (item.type || '').toLowerCase();
        const description = (item.description || '').toLowerCase();
        const searchableText = name + ' ' + type + ' ' + description;
        return searchWords.some(word => searchableText.includes(word));
    });

    if (matches.length === 0) {
        resultsContainer.innerHTML = '<div class="search-result-item"><span class="result-name" style="color:#999;">No items found</span></div>';
        resultsContainer.classList.add('show');
        return;
    }

    resultsContainer.innerHTML = matches.map(item => `
        <div class="search-result-item" onclick="selectSaleItem(${item.id}, '${item.name.replace(/'/g, "\\'")}')">
            <div>
                <div class="result-name">${item.name}</div>
                <div class="result-meta">${item.type || 'No type'} · Qty: ${item.quantity}</div>
            </div>
            <div class="result-price">₹${formatNumber(item.price)}</div>
        </div>
    `).join('');
    resultsContainer.classList.add('show');
}

function hideSaleSearchResults() {
    const resultsContainer = document.getElementById('saleSearchResults');
    if (resultsContainer) {
        resultsContainer.classList.remove('show');
    }
}

function selectSaleItem(itemId, itemName) {
    appState.selectedSaleItem = appState.items.find(i => i.id === itemId);
    if (!appState.selectedSaleItem) return;

    document.getElementById('saleItemId').value = itemId;
    document.getElementById('saleSearchInput').value = itemName;

    // Show selected item indicator
    let selectedDiv = document.querySelector('.sale-selected-item');
    if (!selectedDiv) {
        selectedDiv = document.createElement('div');
        selectedDiv.className = 'sale-selected-item';
        selectedDiv.innerHTML = '<span class="selected-text"></span><span class="remove-selection" onclick="clearSaleSelection()">&times;</span>';
        document.getElementById('saleSearchInput').parentNode.appendChild(selectedDiv);
    }
    selectedDiv.querySelector('.selected-text').textContent = `${itemName} — ₹${formatNumber(appState.selectedSaleItem.price)} — Stock: ${appState.selectedSaleItem.quantity}`;
    selectedDiv.classList.add('show');

    hideSaleSearchResults();
}

function clearSaleSelection() {
    appState.selectedSaleItem = null;
    document.getElementById('saleItemId').value = '';
    document.getElementById('saleSearchInput').value = '';
    const selectedDiv = document.querySelector('.sale-selected-item');
    if (selectedDiv) selectedDiv.classList.remove('show');
}

async function recordSale() {
    const itemId = document.getElementById('saleItemId').value;
    const quantity = parseInt(document.getElementById('saleQuantity').value) || 1;
    const customPrice = document.getElementById('salePrice').value.trim();
    const notes = document.getElementById('saleNotes').value.trim();

    if (!itemId || !appState.selectedSaleItem) {
        alert('Please search and select an item');
        return;
    }

    if (quantity < 1) {
        alert('Quantity must be at least 1');
        return;
    }

    if (quantity > appState.selectedSaleItem.quantity) {
        alert(`Not enough stock. Available: ${appState.selectedSaleItem.quantity}`);
        return;
    }

    // Use custom price if provided and valid, otherwise use item price
    let price = appState.selectedSaleItem.price;
    if (customPrice) {
        const parsedPrice = parseFloat(customPrice);
        if (!isNaN(parsedPrice) && parsedPrice >= 0) {
            price = parsedPrice;
        } else {
            alert('Please enter a valid price (non-negative number)');
            return;
        }
    }

    try {
        await window.API.sales.createSale({
            item_id: parseInt(itemId),
            quantity: quantity,
            price: price,
            description: notes
        });
        alert('✓ Sale recorded!');
        document.getElementById('saleQuantity').value = '1';
        document.getElementById('salePrice').value = '';
        document.getElementById('saleNotes').value = '';
        clearSaleSelection();
        await loadSales();
        await loadItems();
        await loadDashboard();
    } catch (error) {
        console.error('Sale error:', error);
        // The API rejects oversell with a 409 INSUFFICIENT_STOCK envelope.
        if (error && error.code === 'INSUFFICIENT_STOCK') {
            const avail = error.details && error.details.available;
            alert('Not enough stock' + (avail != null ? `. Available: ${avail}` : '') + '.');
        } else {
            alert('Error: ' + (error.message || 'Failed to record sale'));
        }
    }
}

// ========== CAMERA FUNCTIONS ==========
async function startCamera() {
    try {
        const stream = await navigator.mediaDevices.getUserMedia({
            video: { facingMode: 'environment' }
        });

        const video = document.getElementById('cameraPreview');
        video.srcObject = stream;
        appState.cameraStream = stream;

        document.getElementById('startCameraBtn').style.display = 'none';
        document.getElementById('captureCameraBtn').style.display = 'inline-block';
        document.getElementById('stopCameraBtn').style.display = 'inline-block';
    } catch (error) {
        alert('Camera permission denied or not available');
        console.error('Camera error:', error);
    }
}

function captureImage() {
    const video = document.getElementById('cameraPreview');
    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;

    const ctx = canvas.getContext('2d');
    ctx.drawImage(video, 0, 0);

    appState.capturedImageBase64 = canvas.toDataURL('image/jpeg', 0.8);
    document.getElementById('capturedImage').src = appState.capturedImageBase64;

    document.getElementById('cameraPreview').style.display = 'none';
    document.getElementById('imagePreview').style.display = 'block';
    document.getElementById('captureCameraBtn').style.display = 'none';
    document.getElementById('stopCameraBtn').style.display = 'none';
}

function stopCamera() {
    if (appState.cameraStream) {
        appState.cameraStream.getTracks().forEach(track => track.stop());
        appState.cameraStream = null;
    }

    document.getElementById('startCameraBtn').style.display = 'inline-block';
    document.getElementById('captureCameraBtn').style.display = 'none';
    document.getElementById('stopCameraBtn').style.display = 'none';
}

function retakePhoto() {
    document.getElementById('imagePreview').style.display = 'none';
    document.getElementById('cameraPreview').style.display = 'block';
    document.getElementById('captureCameraBtn').style.display = 'inline-block';
    appState.capturedImageBase64 = null;
}

function useImage() {
    if (appState.capturedImageBase64) {
        document.getElementById('imageBase64').value = appState.capturedImageBase64;
        document.getElementById('imagePreview').style.display = 'none';
        document.getElementById('cameraPreview').style.display = 'block';
        document.getElementById('startCameraBtn').style.display = 'inline-block';
        alert('✓ Image ready! Fill in the item details below and click "Add Item"');
    }
}

function resetCameraForm() {
    document.getElementById('itemForm').reset();
    document.getElementById('imageBase64').value = '';
    document.getElementById('imagePreview').style.display = 'none';
    document.getElementById('cameraPreview').style.display = 'block';
    appState.capturedImageBase64 = null;

    if (appState.cameraStream) {
        stopCamera();
    }
}

// ========== CACHE & OFFLINE SUPPORT ==========
function saveToCache(key, data) {
    try {
        localStorage.setItem(`cache_${key}`, JSON.stringify(data));
    } catch (e) {
        console.warn('LocalStorage full or unavailable');
    }
}

function loadFromCache(key) {
    try {
        const data = localStorage.getItem(`cache_${key}`);
        return data ? JSON.parse(data) : null;
    } catch (e) {
        return null;
    }
}

function saveOfflineAction(action, data) {
    try {
        const actions = JSON.parse(localStorage.getItem('offlineActions') || '[]');
        actions.push({ action, data, timestamp: Date.now() });
        localStorage.setItem('offlineActions', JSON.stringify(actions));
    } catch (e) {
        console.warn('Could not save offline action');
    }
}

async function syncOfflineData() {
    try {
        const actions = JSON.parse(localStorage.getItem('offlineActions') || '[]');
        for (const action of actions) {
            if (action.action === 'addItem') {
                await window.API.items.createItem(action.data);
            }
        }
        localStorage.removeItem('offlineActions');
        await loadItems();
        await loadDashboard();
    } catch (error) {
        console.error('Sync error:', error);
    }
}

// ========== PWA INSTALLATION ==========
async function installPWA() {
    if (window.deferredPrompt) {
        window.deferredPrompt.prompt();
        const { outcome } = await window.deferredPrompt.userChoice;
        if (outcome === 'accepted') {
            alert('✓ App installed!');
        }
        window.deferredPrompt = null;
        document.getElementById('installBtn').style.display = 'none';
    }
}

// ========== UTILITIES ==========
function formatNumber(num) {
    return num.toLocaleString('en-IN', { maximumFractionDigits: 2 });
}
