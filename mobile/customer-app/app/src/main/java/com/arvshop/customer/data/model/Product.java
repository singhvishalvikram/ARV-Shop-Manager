package com.arvshop.customer.data.model;

import java.util.Objects;

/**
 * One catalog product. Field names mirror git-pages/data/products.json exactly
 * (see MOBILE-ARCHITECTURE.md "Verified production data shapes").
 * Pure Java — no Android imports — so the parser is JVM-unit-testable.
 */
public final class Product {

    public final long id;
    public final String name;
    public final String category;      // JSON field "type"
    public final String description;
    public final double price;
    public final double mrp;
    public final int discountPercent;  // JSON field "discount_percent"
    public final String imageUrl;      // may be relative ("/images/…") — resolve against base URL
    public final boolean featured;
    public final String badge;         // "" when absent
    public final int sortOrder;
    public final boolean inStock;      // JSON "stock_status" absent or != "out_of_stock"

    public Product(long id, String name, String category, String description,
                   double price, double mrp, int discountPercent, String imageUrl,
                   boolean featured, String badge, int sortOrder, boolean inStock) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
        this.price = price;
        this.mrp = mrp;
        this.discountPercent = discountPercent;
        this.imageUrl = imageUrl;
        this.featured = featured;
        this.badge = badge;
        this.sortOrder = sortOrder;
        this.inStock = inStock;
    }

    public boolean hasDiscount() {
        return discountPercent > 0 && mrp > price;
    }

    public boolean hasBadge() {
        return badge != null && !badge.trim().isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Product)) return false;
        Product p = (Product) o;
        return id == p.id && Double.compare(price, p.price) == 0
                && Double.compare(mrp, p.mrp) == 0 && featured == p.featured
                && inStock == p.inStock && sortOrder == p.sortOrder
                && discountPercent == p.discountPercent
                && Objects.equals(name, p.name) && Objects.equals(category, p.category)
                && Objects.equals(description, p.description)
                && Objects.equals(imageUrl, p.imageUrl) && Objects.equals(badge, p.badge);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
}
