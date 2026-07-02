package com.arvshop.customer.util;

import static org.junit.Assert.assertEquals;

import com.arvshop.customer.data.model.Product;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Phase 12/16 — search + category filtering and display ordering. */
public class CatalogFilterTest {

    private Product p(long id, String name, String category, boolean featured, int sort) {
        return new Product(id, name, category, "desc of " + name, 100, 100, 0,
                "", featured, "", sort, true);
    }

    private final List<Product> all = Arrays.asList(
            p(1, "Zebra Lamp", "Electronics", false, 5),
            p(2, "Alpha Fan", "Electronics", false, 5),
            p(3, "Featured Torch", "Electronics", true, 9),
            p(4, "Kettle", "Kitchen", false, 0));

    @Test
    public void featuredFirstThenSortOrderThenName() {
        List<Product> out = CatalogFilter.apply(all, "", CatalogFilter.ALL_CATEGORIES);
        assertEquals(3, out.get(0).id); // featured wins despite higher sort_order
        assertEquals(4, out.get(1).id); // sort_order 0
        assertEquals(2, out.get(2).id); // "Alpha" before "Zebra"
        assertEquals(1, out.get(3).id);
    }

    @Test
    public void filtersByCategory() {
        List<Product> out = CatalogFilter.apply(all, "", "Kitchen");
        assertEquals(1, out.size());
        assertEquals(4, out.get(0).id);
    }

    @Test
    public void searchMatchesNameAndDescriptionCaseInsensitive() {
        assertEquals(1, CatalogFilter.apply(all, "zEbRa", CatalogFilter.ALL_CATEGORIES).size());
        assertEquals(1, CatalogFilter.apply(all, "desc of Kettle", CatalogFilter.ALL_CATEGORIES).size());
        assertEquals(0, CatalogFilter.apply(all, "nonexistent", CatalogFilter.ALL_CATEGORIES).size());
    }

    @Test
    public void nullProductsYieldEmptyList() {
        assertEquals(0, CatalogFilter.apply(null, "x", "").size());
    }
}
