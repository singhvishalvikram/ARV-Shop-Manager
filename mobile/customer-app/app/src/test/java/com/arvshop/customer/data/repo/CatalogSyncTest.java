package com.arvshop.customer.data.repo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.arvshop.customer.data.model.CatalogVersion;

import org.junit.Test;

/** Phase 09/16 — version.json refresh decision logic. */
public class CatalogSyncTest {

    private static final long NONE = CatalogVersion.NONE;

    @Test
    public void refetchesWhenNothingCached() {
        assertTrue(CatalogSync.shouldRefetch(NONE, 100L, false));
    }

    @Test
    public void servesCacheWhenVersionUnchanged() {
        assertFalse(CatalogSync.shouldRefetch(100L, 100L, false));
        assertFalse(CatalogSync.shouldRefetch(100L, 100L, true)); // even on pull-to-refresh
    }

    @Test
    public void refetchesWhenPublishBumpedVersion() {
        assertTrue(CatalogSync.shouldRefetch(100L, 101L, false));
        assertTrue(CatalogSync.shouldRefetch(101L, 100L, false)); // any change, either direction
    }

    @Test
    public void neverRefetchesWhenOffline() {
        assertFalse(CatalogSync.shouldRefetch(100L, NONE, true));
        assertFalse(CatalogSync.shouldRefetch(NONE, NONE, false));
    }
}
