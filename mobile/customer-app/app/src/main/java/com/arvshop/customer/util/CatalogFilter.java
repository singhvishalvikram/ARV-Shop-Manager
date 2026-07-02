package com.arvshop.customer.util;

import com.arvshop.customer.data.model.Product;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure search/category filtering + display ordering, mirroring the PWA:
 * featured first, then sort_order, then name. Unit-tested in CatalogFilterTest.
 */
public final class CatalogFilter {

    /** Sentinel meaning "no category filter". */
    public static final String ALL_CATEGORIES = "";

    private CatalogFilter() { }

    public static List<Product> apply(List<Product> products, String query, String category) {
        List<Product> out = new ArrayList<>();
        if (products == null) return out;
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (Product p : products) {
            if (!ALL_CATEGORIES.equals(category) && !p.category.equals(category)) continue;
            if (!q.isEmpty()
                    && !p.name.toLowerCase(Locale.ROOT).contains(q)
                    && !p.description.toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(p);
        }
        out.sort((a, b) -> {
            if (a.featured != b.featured) return a.featured ? -1 : 1;
            if (a.sortOrder != b.sortOrder) return Integer.compare(a.sortOrder, b.sortOrder);
            return a.name.compareToIgnoreCase(b.name);
        });
        return out;
    }
}
