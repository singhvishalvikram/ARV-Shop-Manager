package com.arvshop.customer.util;

import java.util.Locale;

/** Money formatting matching the PWA: "₹325" for whole values, "₹325.50" otherwise. */
public final class Formats {

    private Formats() { }

    public static String money(String symbol, double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return symbol + String.format(Locale.US, "%.0f", value);
        }
        return symbol + String.format(Locale.US, "%.2f", value);
    }
}
