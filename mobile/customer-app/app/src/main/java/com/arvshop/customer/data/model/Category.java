package com.arvshop.customer.data.model;

/** Mirrors git-pages/data/categories.json: { name, display_name, sort_order }. */
public final class Category {

    public final String name;
    public final String displayName;
    public final int sortOrder;

    public Category(String name, String displayName, int sortOrder) {
        this.name = name;
        this.displayName = displayName;
        this.sortOrder = sortOrder;
    }
}
