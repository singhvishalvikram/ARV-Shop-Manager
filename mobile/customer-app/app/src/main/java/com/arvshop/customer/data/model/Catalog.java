package com.arvshop.customer.data.model;

import java.util.List;

/** Everything the UI needs, loaded atomically so screens never mix catalog versions. */
public final class Catalog {

    public final List<Product> products;
    public final List<Category> categories;
    public final ShopSettings settings;
    public final long version;
    public final boolean fromCache; // true when served from disk (offline / pre-refresh)

    public Catalog(List<Product> products, List<Category> categories,
                   ShopSettings settings, long version, boolean fromCache) {
        this.products = products;
        this.categories = categories;
        this.settings = settings;
        this.version = version;
        this.fromCache = fromCache;
    }
}
