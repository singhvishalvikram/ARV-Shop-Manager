package com.arvshop.customer.util;

import static org.junit.Assert.assertEquals;

import com.arvshop.customer.data.model.Category;
import com.arvshop.customer.data.model.Product;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Parity gap G1 — category chip resolution. */
public class CategoriesTest {

    private Product p(String type) {
        return new Product(1, "x", type, "", 1, 1, 0, "", false, "", 0, true);
    }

    @Test
    public void prefersCategoriesJsonWhenPresent() {
        List<Category> json = Arrays.asList(new Category("Electronics", "Electronics", 0));
        List<Category> out = Categories.effective(json, Arrays.asList(p("Kitchen")));
        assertEquals(1, out.size());
        assertEquals("Electronics", out.get(0).name); // json wins over derived
    }

    @Test
    public void derivesDistinctSortedTypesWhenJsonEmpty() {
        List<Product> products = Arrays.asList(
                p("Kitchen"), p("Electronics"), p("Kitchen"), p("Apparel"));
        List<Category> out = Categories.effective(new ArrayList<>(), products);
        assertEquals(3, out.size());
        assertEquals("Apparel", out.get(0).name);       // sorted
        assertEquals("Electronics", out.get(1).name);
        assertEquals("Kitchen", out.get(2).name);       // de-duplicated
    }

    @Test
    public void handlesNullsSafely() {
        assertEquals(0, Categories.effective(null, null).size());
        assertEquals(0, Categories.effective(Collections.emptyList(),
                Collections.emptyList()).size());
    }
}
