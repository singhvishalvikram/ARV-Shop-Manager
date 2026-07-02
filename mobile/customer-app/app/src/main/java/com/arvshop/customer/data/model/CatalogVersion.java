package com.arvshop.customer.data.model;

/** Mirrors git-pages/data/version.json: { v, at, count }. v is the cache-bust signal. */
public final class CatalogVersion {

    public static final long NONE = -1L;

    public final long v;
    public final String at;
    public final int count;

    public CatalogVersion(long v, String at, int count) {
        this.v = v;
        this.at = at;
        this.count = count;
    }
}
