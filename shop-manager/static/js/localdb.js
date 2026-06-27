/**
 * IndexedDB Local Database Layer for Shop Manager
 * 
 * This module provides a local-first data storage solution.
 * All data is stored in the browser's IndexedDB, survives page refreshes,
 * works offline, and can be exported/imported as JSON for transfer to the Android app.
 * 
 * Database: shop_manager_db
 * Stores: items, sales, app_settings
 */

const DB_NAME = 'shop_manager_db';
const DB_VERSION = 1;

const STORES = {
    items: 'items',
    sales: 'sales',
    settings: 'settings',
    sync_queue: 'sync_queue'  // pending changes to sync to server
};

let dbInstance = null;

/**
 * Open (or create) the IndexedDB database
 */
function openDB() {
    return new Promise((resolve, reject) => {
        if (dbInstance) {
            resolve(dbInstance);
            return;
        }

        const request = indexedDB.open(DB_NAME, DB_VERSION);

        request.onupgradeneeded = (event) => {
            const db = event.target.result;

            // Items store - keyed by local_id (auto-increment)
            if (!db.objectStoreNames.contains(STORES.items)) {
                const itemStore = db.createObjectStore(STORES.items, { keyPath: 'local_id' });
                itemStore.createIndex('server_id', 'server_id', { unique: false });
                itemStore.createIndex('name', 'name', { unique: false });
                itemStore.createIndex('type', 'type', { unique: false });
                itemStore.createIndex('updated_at', 'updated_at', { unique: false });
            }

            // Sales store
            if (!db.objectStoreNames.contains(STORES.sales)) {
                const saleStore = db.createObjectStore(STORES.sales, { keyPath: 'local_id' });
                saleStore.createIndex('server_id', 'server_id', { unique: false });
                saleStore.createIndex('item_id', 'item_id', { unique: false });
                saleStore.createIndex('created_at', 'created_at', { unique: false });
            }

            // Settings store
            if (!db.objectStoreNames.contains(STORES.settings)) {
                db.createObjectStore(STORES.settings, { keyPath: 'key' });
            }

            // Sync queue - pending changes for server sync
            if (!db.objectStoreNames.contains(STORES.sync_queue)) {
                db.createObjectStore(STORES.sync_queue, { keyPath: 'id', autoIncrement: true });
            }
        };

        request.onsuccess = (event) => {
            dbInstance = event.target.result;
            resolve(dbInstance);
        };

        request.onerror = (event) => {
            console.error('IndexedDB open error:', event.target.error);
            reject(event.target.error);
        };
    });
}

/**
 * Generic: get all records from a store
 */
function getAll(storeName) {
    return new Promise(async (resolve, reject) => {
        try {
            const db = await openDB();
            const tx = db.transaction(storeName, 'readonly');
            const store = tx.objectStore(storeName);
            const request = store.getAll();
            request.onsuccess = () => resolve(request.result);
            request.onerror = () => reject(request.error);
        } catch (err) {
            reject(err);
        }
    });
}

/**
 * Generic: put (insert or update) a record
 */
function put(storeName, record) {
    return new Promise(async (resolve, reject) => {
        try {
            const db = await openDB();
            const tx = db.transaction(storeName, 'readwrite');
            const store = tx.objectStore(storeName);
            const request = store.put(record);
            request.onsuccess = () => resolve(request.result);
            request.onerror = () => reject(request.error);
        } catch (err) {
            reject(err);
        }
    });
}

/**
 * Generic: delete a record by key
 */
function remove(storeName, key) {
    return new Promise(async (resolve, reject) => {
        try {
            const db = await openDB();
            const tx = db.transaction(storeName, 'readwrite');
            const store = tx.objectStore(storeName);
            const request = store.delete(key);
            request.onsuccess = () => resolve();
            request.onerror = () => reject(request.error);
        } catch (err) {
            reject(err);
        }
    });
}

/**
 * Generic: clear all records in a store
 */
function clearStore(storeName) {
    return new Promise(async (resolve, reject) => {
        try {
            const db = await openDB();
            const tx = db.transaction(storeName, 'readwrite');
            const store = tx.objectStore(storeName);
            const request = store.clear();
            request.onsuccess = () => resolve();
            request.onerror = () => reject(request.error);
        } catch (err) {
            reject(err);
        }
    });
}

// ==================== ITEMS ====================

/**
 * Get all items from local DB
 */
async function getLocalItems() {
    return await getAll(STORES.items);
}

/**
 * Get a single item by local_id
 */
async function getLocalItem(localId) {
    const db = await openDB();
    return new Promise((resolve, reject) => {
        const tx = db.transaction(STORES.items, 'readonly');
        const store = tx.objectStore(STORES.items);
        const request = store.get(localId);
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
    });
}

/**
 * Get item by server_id
 */
async function getLocalItemByServerId(serverId) {
    const db = await openDB();
    return new Promise((resolve, reject) => {
        const tx = db.transaction(STORES.items, 'readonly');
        const store = tx.objectStore(STORES.items);
        const index = store.index('server_id');
        const request = index.get(serverId);
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
    });
}

/**
 * Save item to local DB (insert or update)
 * Returns the saved record with local_id
 */
async function saveLocalItem(item) {
    const now = new Date().toISOString();
    const record = {
        ...item,
        updated_at: now,
        synced: item.synced !== undefined ? item.synced : false
    };
    if (!record.local_id) {
        record.created_at = now;
        record.synced = false;
    }
    await put(STORES.items, record);
    return record;
}

/**
 * Delete item from local DB by local_id
 */
async function deleteLocalItem(localId) {
    await remove(STORES.items, localId);
}

// ==================== SALES ====================

/**
 * Get all sales from local DB
 */
async function getLocalSales() {
    return await getAll(STORES.sales);
}

/**
 * Get today's sales
 */
async function getLocalSalesToday() {
    const today = new Date().toISOString().split('T')[0];
    const allSales = await getLocalSales();
    return allSales.filter(s => s.created_at && s.created_at.startsWith(today));
}

/**
 * Save sale to local DB
 */
async function saveLocalSale(sale) {
    const now = new Date().toISOString();
    const record = {
        ...sale,
        created_at: sale.created_at || now,
        updated_at: now,
        synced: sale.synced !== undefined ? sale.synced : false
    };
    await put(STORES.sales, record);
    return record;
}

/**
 * Delete sale from local DB
 */
async function deleteLocalSale(localId) {
    await remove(STORES.sales, localId);
}

// ==================== SETTINGS ====================

/**
 * Get a setting value
 */
async function getSetting(key) {
    const db = await openDB();
    return new Promise((resolve, reject) => {
        const tx = db.transaction(STORES.settings, 'readonly');
        const store = tx.objectStore(STORES.settings);
        const request = store.get(key);
        request.onsuccess = () => {
            const result = request.result;
            resolve(result ? result.value : null);
        };
        request.onerror = () => reject(request.error);
    });
}

/**
 * Set a setting value
 */
async function setSetting(key, value) {
    await put(STORES.settings, { key, value });
}

// ==================== BACKUP / RESTORE ====================

/**
 * Export all local data as a JSON object
 * This is the file you'll transfer to the Android app
 */
async function exportAllData() {
    const items = await getLocalItems();
    const sales = await getLocalSales();
    const settings = await getAll(STORES.settings);

    return {
        version: '3.0',
        exported_at: new Date().toISOString(),
        app: 'Shop Manager',
        data: {
            items: items.map(item => ({
                local_id: item.local_id,
                server_id: item.server_id,
                name: item.name,
                type: item.type,
                description: item.description || '',
                price: item.price,
                quantity: item.quantity,
                location: item.location || '',
                image_url: item.image_url || '',
                created_at: item.created_at,
                updated_at: item.updated_at
            })),
            sales: sales.map(sale => ({
                local_id: sale.local_id,
                server_id: sale.server_id,
                item_id: sale.item_id,
                item_name: sale.item_name || '',
                quantity_sold: sale.quantity_sold || sale.quantity || 0,
                sale_price: sale.sale_price || sale.price || 0,
                description: sale.description || '',
                created_at: sale.created_at
            })),
            settings: settings.reduce((acc, s) => {
                acc[s.key] = s.value;
                return acc;
            }, {})
        },
        stats: {
            total_items: items.length,
            total_sales: sales.length
        }
    };
}

/**
 * Download all data as a JSON file backup
 */
async function downloadBackup() {
    try {
        const data = await exportAllData();
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        const date = new Date().toISOString().split('T')[0];
        a.download = `shop-manager-backup-${date}.json`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        return true;
    } catch (error) {
        console.error('Backup download failed:', error);
        throw error;
    }
}

/**
 * Import data from a JSON backup file
 * Merges with existing data (does not clear first unless specified)
 */
async function importData(jsonFile, options = {}) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = async (event) => {
            try {
                const imported = JSON.parse(event.target.result);

                // Validate format
                if (!imported.data || !imported.data.items) {
                    throw new Error('Invalid backup format');
                }

                const clearFirst = options.clear_existing === true;

                if (clearFirst) {
                    await clearStore(STORES.items);
                    await clearStore(STORES.sales);
                }

                let importedItems = 0;
                let importedSales = 0;
                let skippedItems = 0;
                let skippedSales = 0;

                // Import items
                for (const item of imported.data.items) {
                    try {
                        if (clearFirst) {
                            await saveLocalItem(item);
                            importedItems++;
                        } else {
                            // Check if item already exists by server_id
                            const existing = item.server_id
                                ? await getLocalItemByServerId(item.server_id)
                                : null;
                            if (existing) {
                                // Update existing if imported version is newer
                                const importedDate = new Date(item.updated_at || 0);
                                const existingDate = new Date(existing.updated_at || 0);
                                if (importedDate > existingDate) {
                                    await saveLocalItem({ ...item, local_id: existing.local_id });
                                    importedItems++;
                                } else {
                                    skippedItems++;
                                }
                            } else {
                                await saveLocalItem(item);
                                importedItems++;
                            }
                        }
                    } catch (err) {
                        console.warn('Failed to import item:', item.name, err);
                        skippedItems++;
                    }
                }

                // Import sales
                for (const sale of (imported.data.sales || [])) {
                    try {
                        await saveLocalSale(sale);
                        importedSales++;
                    } catch (err) {
                        console.warn('Failed to import sale:', err);
                        skippedSales++;
                    }
                }

                // Import settings
                if (imported.data.settings) {
                    for (const [key, value] of Object.entries(imported.data.settings)) {
                        if (key !== 'shop_manager_pin') { // Don't overwrite PIN
                            await setSetting(key, value);
                        }
                    }
                }

                resolve({
                    imported_items: importedItems,
                    imported_sales: importedSales,
                    skipped_items: skippedItems,
                    skipped_sales: skippedSales,
                    total_items: (await getLocalItems()).length,
                    total_sales: (await getLocalSales()).length
                });
            } catch (parseError) {
                reject(new Error('Failed to parse backup file: ' + parseError.message));
            }
        };
        reader.onerror = () => reject(new Error('Failed to read file'));
        reader.readAsText(jsonFile);
    });
}

/**
 * Get database stats for display
 */
async function getDatabaseStats() {
    const items = await getLocalItems();
    const sales = await getLocalSales();
    const todaySales = await getLocalSalesToday();
    const totalStockValue = items.reduce((sum, item) => sum + (item.price * item.quantity), 0);
    const todayRevenue = todaySales.reduce((sum, sale) => sum + ((sale.sale_price || sale.price || 0) * (sale.quantity_sold || sale.quantity || 0)), 0);

    return {
        total_items: items.length,
        total_stock: items.reduce((sum, item) => sum + item.quantity, 0),
        total_stock_value: totalStockValue,
        total_sales: sales.length,
        today_sales: todaySales.length,
        today_revenue: todayRevenue,
        avg_price: items.length > 0 ? (items.reduce((sum, item) => sum + item.price, 0) / items.length) : 0
    };
}

// ==================== AUTO-BACKUP ====================

/**
 * Schedule automatic backup reminders
 */
let backupReminderInterval = null;

function startBackupReminders(intervalMinutes = 60) {
    if (backupReminderInterval) clearInterval(backupReminderInterval);
    
    const lastReminderKey = 'shop_manager_last_backup_reminder';
    
    backupReminderInterval = setInterval(async () => {
        const last = await getSetting(lastReminderKey);
        const now = Date.now();
        
        if (!last || (now - parseInt(last)) > intervalMinutes * 60 * 1000) {
            // Show a subtle notification
            if (typeof showBackupReminder === 'function') {
                showBackupReminder();
            }
            await setSetting(lastReminderKey, now.toString());
        }
    }, 5 * 60 * 1000); // Check every 5 minutes
}

function stopBackupReminders() {
    if (backupReminderInterval) {
        clearInterval(backupReminderInterval);
        backupReminderInterval = null;
    }
}

/**
 * Save app state for session persistence
 */
async function saveAppState(state) {
    await setSetting('app_state', JSON.stringify(state));
}

/**
 * Restore app state from session
 */
async function loadAppState() {
    const data = await getSetting('app_state');
    return data ? JSON.parse(data) : null;
}

/**
 * Mark an item record as synced with server
 */
async function markItemSynced(localId, serverId) {
    const item = await getLocalItem(localId);
    if (item) {
        item.server_id = serverId;
        item.synced = true;
        item.synced_at = new Date().toISOString();
        await saveLocalItem(item);
    }
}

// ==================== EXPORT (for use by app.js) ====================

window.LocalDB = {
    // Core
    openDB,
    // Items
    getLocalItems,
    getLocalItem,
    getLocalItemByServerId,
    saveLocalItem,
    deleteLocalItem,
    markItemSynced,
    // Sales
    getLocalSales,
    getLocalSalesToday,
    saveLocalSale,
    deleteLocalSale,
    // Settings
    getSetting,
    setSetting,
    // Backup/Restore
    exportAllData,
    downloadBackup,
    importData,
    getDatabaseStats,
    // State
    saveAppState,
    loadAppState,
    // Reminders
    startBackupReminders,
    stopBackupReminders,
    // Constants
    STORES
};
