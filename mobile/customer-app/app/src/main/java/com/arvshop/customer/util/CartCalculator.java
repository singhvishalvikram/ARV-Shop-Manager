package com.arvshop.customer.util;

import com.arvshop.customer.data.model.CartItem;

import java.util.List;

/** Pure cart math — unit-tested in CartCalculatorTest (Phase 07). */
public final class CartCalculator {

    private CartCalculator() { }

    public static double total(List<CartItem> items) {
        double sum = 0;
        if (items == null) return 0;
        for (CartItem it : items) {
            sum += it.lineTotal();
        }
        return sum;
    }

    public static int itemCount(List<CartItem> items) {
        int n = 0;
        if (items == null) return 0;
        for (CartItem it : items) {
            n += it.qty;
        }
        return n;
    }
}
