package com.arvshop.customer.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.arvshop.customer.data.model.CatalogVersion;
import com.arvshop.customer.data.model.Category;
import com.arvshop.customer.data.model.Product;
import com.arvshop.customer.data.model.ShopSettings;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

/**
 * Phase 03/16 — parser contract tests against fixtures copied from the REAL
 * git-pages/data/ production output (plus injected malformed rows).
 */
public class CatalogParserTest {

    private static String fixture(String name) throws IOException {
        try (InputStream in = CatalogParserTest.class
                .getResourceAsStream("/fixtures/" + name)) {
            if (in == null) throw new IOException("missing fixture " + name);
            try (Scanner s = new Scanner(in, StandardCharsets.UTF_8.name())) {
                return s.useDelimiter("\\A").next();
            }
        }
    }

    @Test
    public void parsesRealProductShape() throws Exception {
        List<Product> products = CatalogParser.parseProducts(fixture("products.json"));
        Product torch = products.get(0);
        assertEquals(54, torch.id);
        assertEquals("AKARI Led Torch", torch.name);
        assertEquals("Electronics", torch.category);
        assertEquals(325.0, torch.price, 0.001);
        assertEquals(390.0, torch.mrp, 0.001);
        assertEquals(17, torch.discountPercent);
        assertEquals("/images/item_54_1780731939.128437.jpg", torch.imageUrl);
        assertTrue(torch.hasDiscount());
        assertTrue(torch.hasBadge());
        assertTrue(torch.inStock); // no stock_status field ⇒ in stock
    }

    @Test
    public void skipsMalformedRowsInsteadOfCrashing() throws Exception {
        List<Product> products = CatalogParser.parseProducts(fixture("products.json"));
        // Fixture has 5 rows; the no-id row and the blank-name row must be dropped.
        assertEquals(3, products.size());
    }

    @Test
    public void parsesOutOfStockStatus() throws Exception {
        List<Product> products = CatalogParser.parseProducts(fixture("products.json"));
        Product kettle = products.get(2);
        assertEquals("Sold Out Kettle", kettle.name);
        assertFalse(kettle.inStock);
    }

    @Test
    public void noDiscountWhenMrpEqualsPrice() throws Exception {
        List<Product> products = CatalogParser.parseProducts(fixture("products.json"));
        Product fan = products.get(1);
        assertFalse(fan.hasDiscount());
        assertFalse(fan.hasBadge());
        assertTrue(fan.featured);
    }

    @Test
    public void parsesSettingsFlags() throws Exception {
        ShopSettings s = CatalogParser.parseSettings(fixture("settings.json"));
        assertEquals("ARV ENTERPRISES", s.appTitle());
        assertEquals("917052696929", s.whatsappNumber());
        assertEquals("₹", s.currencySymbol());
        assertTrue(s.showSearch());
        assertTrue(s.showMrp());
        assertEquals(0, s.maxProducts());
    }

    @Test
    public void parsesVersion() throws Exception {
        CatalogVersion v = CatalogParser.parseVersion(fixture("version.json"));
        assertEquals(1782471119L, v.v);
        assertEquals(51, v.count);
    }

    @Test
    public void parsesCategories() throws Exception {
        List<Category> cats = CatalogParser.parseCategories(fixture("categories.json"));
        assertEquals(2, cats.size());
        assertEquals("Kitchen Items", cats.get(1).displayName);
    }
}
