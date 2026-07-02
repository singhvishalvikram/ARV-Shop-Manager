package com.arvshop.customer.core;

import android.content.Context;

import com.arvshop.customer.BuildConfig;
import com.arvshop.customer.data.local.CartStore;
import com.arvshop.customer.data.local.DiskCache;
import com.arvshop.customer.data.repo.CartRepository;
import com.arvshop.customer.data.repo.CatalogRepository;
import com.arvshop.customer.util.ImageLoader;

/**
 * Manual dependency wiring. Intentional alternative to a DI framework at this
 * project size (see MOBILE-ARCHITECTURE.md — dependency minimalism guardrail).
 */
public final class ServiceLocator {

    private static CatalogRepository catalogRepository;
    private static CartRepository cartRepository;
    private static ImageLoader imageLoader;

    private ServiceLocator() { }

    public static synchronized void init(Context appContext) {
        if (catalogRepository != null) return;
        DiskCache diskCache = new DiskCache(appContext.getCacheDir());
        catalogRepository = new CatalogRepository(
                BuildConfig.CATALOG_BASE_URL, diskCache, AppExecutors.get());
        cartRepository = new CartRepository(new CartStore(appContext), AppExecutors.get());
        imageLoader = new ImageLoader(appContext.getCacheDir(), BuildConfig.CATALOG_BASE_URL);
    }

    public static CatalogRepository catalog() {
        return catalogRepository;
    }

    public static CartRepository cart() {
        return cartRepository;
    }

    public static ImageLoader images() {
        return imageLoader;
    }
}
