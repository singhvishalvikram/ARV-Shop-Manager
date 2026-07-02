package com.arvshop.admin.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Client validation mirrors the server Pydantic constraints (schemas.py). */
public class ItemValidatorTest {

    @Test
    public void validInputHasNoErrors() {
        ItemValidator.Errors e = ItemValidator.validate("Torch", "Electronics", "325", "10");
        assertFalse(e.hasErrors());
    }

    @Test
    public void flagsMissingNameTypeAndBadNumbers() {
        ItemValidator.Errors e = ItemValidator.validate("", "", "0", "-1");
        assertTrue(e.hasErrors());
        assertEquals("Name is required", e.name);
        assertEquals("Category is required", e.type);
        assertTrue(e.price != null);       // price must be > 0
        assertTrue(e.quantity != null);    // qty must be >= 0
    }

    @Test
    public void numberParsingIsLenientButSafe() {
        assertEquals(Double.valueOf(325.5), ItemValidator.parsePositiveDouble("325.5"));
        assertNull(ItemValidator.parsePositiveDouble("abc"));
        assertEquals(Integer.valueOf(0), ItemValidator.parseNonNegativeInt("0"));
        assertNull(ItemValidator.parseNonNegativeInt("-3"));
        assertNull(ItemValidator.parseNonNegativeInt("x"));
    }
}
