package com.arvshop.customer.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Phase 11/16 — money formatting parity with the PWA. */
public class FormatsTest {

    @Test
    public void wholeValuesHaveNoDecimals() {
        assertEquals("₹325", Formats.money("₹", 325.0));
        assertEquals("₹0", Formats.money("₹", 0.0));
    }

    @Test
    public void fractionalValuesShowTwoDecimals() {
        assertEquals("₹850.50", Formats.money("₹", 850.5));
        assertEquals("₹1500.50", Formats.money("₹", 1500.501)); // rounds
    }
}
