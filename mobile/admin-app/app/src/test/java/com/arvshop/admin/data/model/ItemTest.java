package com.arvshop.admin.data.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.arvshop.admin.TestFixtures;

import org.json.JSONObject;
import org.junit.Test;

/** Item mapping against the exact live `_row_to_item` shape (booleans as 1/0). */
public class ItemTest {

    @Test
    public void parsesLiveItemShape() throws Exception {
        Item item = Item.fromJson(new JSONObject(TestFixtures.load("item.json")));
        assertEquals(1, item.id);
        assertEquals("Test Torch", item.name);
        assertEquals("Electronics", item.type);
        assertEquals(325.0, item.price, 0.001);
        assertEquals(390.0, item.mrp, 0.001);
        assertEquals(260.0, item.purchaseCost, 0.001);
        assertEquals(10, item.quantity);
        assertEquals("Shelf A", item.location);
        assertTrue(item.visible);       // 1 → true
        assertFalse(item.featured);     // 0 → false
        assertEquals("New", item.badge);
        assertFalse(item.isOutOfStock());
    }

    @Test
    public void outOfStockWhenQuantityZero() throws Exception {
        JSONObject o = new JSONObject(TestFixtures.load("item.json"));
        o.put("quantity", 0);
        o.put("stock_status", "out_of_stock");
        Item item = Item.fromJson(o);
        assertTrue(item.isOutOfStock());
    }
}
