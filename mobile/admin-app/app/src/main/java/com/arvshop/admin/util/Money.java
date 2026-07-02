package com.arvshop.admin.util;

import java.util.Locale;

/** Money formatting: whole values without decimals, else two places. */
public final class Money {

    private Money() { }

    public static String format(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return "₹" + String.format(Locale.US, "%,.0f", value);
        }
        return "₹" + String.format(Locale.US, "%,.2f", value);
    }
}
