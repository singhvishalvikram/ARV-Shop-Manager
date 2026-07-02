package com.arvshop.customer.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.arvshop.customer.data.model.CartItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Phase 08/16 — checkout message + wa.me URL building. */
public class WhatsAppCheckoutTest {

    private final List<CartItem> items = Arrays.asList(
            new CartItem(54, "AKARI Led Torch", 325.0, "", 2),
            new CartItem(56, "Sold Out Kettle", 850.5, "", 1));

    @Test
    public void messageListsLinesAndTotal() {
        String msg = WhatsAppCheckout.buildMessage(items, "ARV ENTERPRISES", "₹");
        assertTrue(msg.contains("Hello ARV ENTERPRISES!"));
        assertTrue(msg.contains("1. AKARI Led Torch"));
        assertTrue(msg.contains("Qty: 2 × ₹325 = ₹650"));
        assertTrue(msg.contains("2. Sold Out Kettle"));
        assertTrue(msg.contains("Total: ₹1500.50"));
    }

    @Test
    public void urlUsesDigitsOnlyPhoneAndEncodesText() {
        String url = WhatsAppCheckout.buildUrl("+91 70526-96929", "Order: torch & fan");
        assertTrue(url.startsWith("https://wa.me/917052696929?text="));
        assertFalse(url.contains(" "));   // spaces must be encoded
        assertFalse(url.contains("&f"));  // '&' inside the text must be encoded
        assertTrue(url.contains("%26"));
    }

    @Test
    public void productionNumberFormatPassesThrough() {
        // settings.json stores the number as digits already: "917052696929"
        String url = WhatsAppCheckout.buildUrl("917052696929", "hi");
        assertEquals("https://wa.me/917052696929?text=hi", url);
    }
}
