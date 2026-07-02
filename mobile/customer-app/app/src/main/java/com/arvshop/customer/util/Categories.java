package com.arvshop.customer.util;

import com.arvshop.customer.data.model.Category;
import com.arvshop.customer.data.model.Product;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves the category chips to display. Prefers the generated {@code categories.json};
 * when that is empty/missing it derives distinct {@code product.type} values — matching
 * the production PWA (git-pages/app.js), which builds categories from product types.
 * Pure Java — unit-tested in CategoriesTest (parity gap G1).
 */
public final class Categories {

    private Categories() { }

    public static List<Category> effective(List<Category> fromJson, List<Product> products) {
        if (fromJson != null && !fromJson.isEmpty()) {
            return fromJson;
        }
        Set<String> types = new LinkedHashSet<>();
        if (products != null) {
            for (Product p : products) {
                if (p.category != null && !p.category.trim().isEmpty()) {
                    types.add(p.category);
                }
            }
        }
        List<String> sorted = new ArrayList<>(types);
        sorted.sort(String::compareToIgnoreCase); // PWA sorts category names
        List<Category> out = new ArrayList<>(sorted.size());
        int order = 0;
        for (String type : sorted) {
            out.add(new Category(type, type, order++));
        }
        return out;
    }
}
