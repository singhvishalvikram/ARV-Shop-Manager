package com.arvshop.customer.util;

import static org.junit.Assert.assertEquals;

import com.arvshop.customer.data.model.CartItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Phase 07/16 — pure cart math. */
public class CartCalculatorTest {

    @Test
    public void totalsSumLineTotals() {
        List<CartItem> items = Arrays.asList(
                new CartItem(1, "Torch", 325.0, "", 2),
                new CartItem(2, "Kettle", 850.5, "", 1));
        assertEquals(325.0 * 2 + 850.5, CartCalculator.total(items), 0.001);
        assertEquals(3, CartCalculator.itemCount(items));
    }

    @Test
    public void emptyAndNullCartsAreZero() {
        assertEquals(0.0, CartCalculator.total(Collections.emptyList()), 0.001);
        assertEquals(0.0, CartCalculator.total(null), 0.001);
        assertEquals(0, CartCalculator.itemCount(null));
    }
}
