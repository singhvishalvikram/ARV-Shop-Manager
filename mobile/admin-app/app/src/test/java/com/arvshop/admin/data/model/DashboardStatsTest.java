package com.arvshop.admin.data.model;

import static org.junit.Assert.assertEquals;

import com.arvshop.admin.TestFixtures;

import org.json.JSONObject;
import org.junit.Test;

public class DashboardStatsTest {

    @Test
    public void parsesLiveDashboard() throws Exception {
        DashboardStats s = DashboardStats.fromJson(new JSONObject(TestFixtures.load("dashboard.json")));
        assertEquals(1, s.totalItems);
        assertEquals(10, s.totalQuantity);
        assertEquals(3250.0, s.stockValue, 0.001);
        assertEquals(3900.0, s.stockMrp, 0.001);
        assertEquals(1, s.typeBreakdown.size());
        assertEquals("Electronics", s.typeBreakdown.get(0).type);
        assertEquals(1, s.recentItems.size());
        assertEquals("Test Torch", s.recentItems.get(0).name);
    }
}
