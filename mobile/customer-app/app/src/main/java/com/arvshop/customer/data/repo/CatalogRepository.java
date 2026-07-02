package com.arvshop.customer.data.repo;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.arvshop.customer.core.AppExecutors;
import com.arvshop.customer.core.Result;
import com.arvshop.customer.data.local.DiskCache;
import com.arvshop.customer.data.model.Catalog;
import com.arvshop.customer.data.model.CatalogVersion;
import com.arvshop.customer.data.model.Category;
import com.arvshop.customer.data.model.Product;
import com.arvshop.customer.data.model.ShopSettings;
import com.arvshop.customer.data.remote.CatalogParser;
import com.arvshop.customer.data.remote.HttpClient;

import org.json.JSONException;

import java.io.IOException;
import java.util.List;

/**
 * Cache-then-network catalog source (Phases 05/09/10):
 *  1. Serve the disk-cached catalog immediately (instant cold start, full offline).
 *  2. Fetch version.json; refetch the three data files only when `v` changed.
 *  3. Persist the new snapshot atomically, then publish it.
 * Reads ONLY the generated pipeline output on GitHub Pages — never any admin API.
 */
public class CatalogRepository {

    private static final String F_PRODUCTS = "products.json";
    private static final String F_CATEGORIES = "categories.json";
    private static final String F_SETTINGS = "settings.json";
    private static final String F_VERSION = "version.json";

    private final String baseUrl;
    private final DiskCache cache;
    private final AppExecutors executors;

    private final MutableLiveData<Result<Catalog>> catalog = new MutableLiveData<>();
    private final MutableLiveData<Boolean> refreshing = new MutableLiveData<>(false);

    public CatalogRepository(String baseUrl, DiskCache cache, AppExecutors executors) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.cache = cache;
        this.executors = executors;
    }

    public LiveData<Result<Catalog>> catalog() {
        return catalog;
    }

    public LiveData<Boolean> refreshing() {
        return refreshing;
    }

    /** Call on app start and on pull-to-refresh. Safe to call repeatedly. */
    public void load(boolean force) {
        if (Boolean.TRUE.equals(refreshing.getValue())) return;
        refreshing.postValue(true);
        if (catalog.getValue() == null) {
            catalog.postValue(Result.loading());
        }
        executors.network().execute(() -> {
            Catalog cached = readCacheQuietly();
            if (cached != null) {
                catalog.postValue(Result.success(cached));
            }
            try {
                long remoteV = fetchRemoteVersion();
                long cachedV = cached != null ? cached.version : CatalogVersion.NONE;
                if (CatalogSync.shouldRefetch(cachedV, remoteV, force)) {
                    Catalog fresh = fetchAndPersist(remoteV);
                    catalog.postValue(Result.success(fresh));
                } else if (cached == null && remoteV == CatalogVersion.NONE) {
                    catalog.postValue(Result.error("offline_no_cache", null));
                }
            } catch (IOException | JSONException e) {
                // Network/parse failure: keep serving cache if we have it (Phase 12).
                catalog.postValue(cached != null
                        ? Result.error("refresh_failed", cached)
                        : Result.error("offline_no_cache", null));
            } finally {
                refreshing.postValue(false);
            }
        });
    }

    private long fetchRemoteVersion() {
        try {
            return CatalogParser.parseVersion(
                    HttpClient.getString(baseUrl + "/data/" + F_VERSION)).v;
        } catch (IOException | JSONException e) {
            return CatalogVersion.NONE;
        }
    }

    private Catalog fetchAndPersist(long remoteV) throws IOException, JSONException {
        String productsJson = HttpClient.getString(baseUrl + "/data/" + F_PRODUCTS);
        String categoriesJson = HttpClient.getString(baseUrl + "/data/" + F_CATEGORIES);
        String settingsJson = HttpClient.getString(baseUrl + "/data/" + F_SETTINGS);

        // Parse BEFORE persisting so a bad payload never poisons the cache.
        List<Product> products = CatalogParser.parseProducts(productsJson);
        List<Category> categories = CatalogParser.parseCategories(categoriesJson);
        ShopSettings settings = CatalogParser.parseSettings(settingsJson);

        cache.put(F_PRODUCTS, productsJson);
        cache.put(F_CATEGORIES, categoriesJson);
        cache.put(F_SETTINGS, settingsJson);
        cache.put(F_VERSION, "{\"v\":" + remoteV + "}");

        return new Catalog(products, categories, settings, remoteV, false);
    }

    private Catalog readCacheQuietly() {
        try {
            String p = cache.get(F_PRODUCTS);
            String c = cache.get(F_CATEGORIES);
            String s = cache.get(F_SETTINGS);
            String v = cache.get(F_VERSION);
            if (p == null || c == null || s == null) return null;
            long version = v != null ? CatalogParser.parseVersion(v).v : CatalogVersion.NONE;
            return new Catalog(CatalogParser.parseProducts(p),
                    CatalogParser.parseCategories(c),
                    CatalogParser.parseSettings(s), version, true);
        } catch (JSONException e) {
            return null; // corrupt cache is treated as no cache
        }
    }
}
