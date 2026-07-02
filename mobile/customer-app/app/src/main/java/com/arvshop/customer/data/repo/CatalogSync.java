package com.arvshop.customer.data.repo;

import com.arvshop.customer.data.model.CatalogVersion;

/**
 * Pure decision logic for version.json-driven refresh (Phase 09).
 * Kept free of Android/IO so it is trivially unit-testable (CatalogSyncTest).
 */
public final class CatalogSync {

    private CatalogSync() { }

    /**
     * @param cachedVersion version of the catalog on disk, or CatalogVersion.NONE
     * @param remoteVersion version reported by version.json, or CatalogVersion.NONE on fetch failure
     * @param force         user asked for pull-to-refresh
     * @return true when the full catalog (products/categories/settings) must be refetched
     */
    public static boolean shouldRefetch(long cachedVersion, long remoteVersion, boolean force) {
        if (remoteVersion == CatalogVersion.NONE) return false; // network failed → serve cache
        if (cachedVersion == CatalogVersion.NONE) return true;  // nothing cached yet
        if (force) return remoteVersion != cachedVersion;       // refresh only if changed
        return remoteVersion != cachedVersion;
    }
}
