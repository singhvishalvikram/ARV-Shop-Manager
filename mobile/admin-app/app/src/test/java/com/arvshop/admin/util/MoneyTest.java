package com.arvshop.admin.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MoneyTest {

    @Test
    public void wholeValuesNoDecimals() {
        assertEquals("₹3,250", Money.format(3250.0));
        assertEquals("₹0", Money.format(0.0));
    }

    @Test
    public void fractionalTwoDecimals() {
        assertEquals("₹325.50", Money.format(325.5));
    }
}
