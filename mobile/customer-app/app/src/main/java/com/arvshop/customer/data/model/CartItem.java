package com.arvshop.customer.data.model;

/**
 * A cart line. Snapshot of the product at add-time (name/price), consistent with
 * the PWA's localStorage cart. Stored locally only — no PII, nothing leaves the device
 * until the user explicitly opens WhatsApp.
 */
public final class CartItem {

    public final long productId;
    public final String name;
    public final double price;
    public final String imageUrl;
    public final int qty;

    public CartItem(long productId, String name, double price, String imageUrl, int qty) {
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.imageUrl = imageUrl;
        this.qty = qty;
    }

    public CartItem withQty(int newQty) {
        return new CartItem(productId, name, price, imageUrl, newQty);
    }

    public double lineTotal() {
        return price * qty;
    }
}
