package com.arvshop.admin.data.repo;

import com.arvshop.admin.data.model.DashboardStats;
import com.arvshop.admin.data.remote.ApiClient;
import com.arvshop.admin.data.remote.ApiException;

/** GET /api/v1/dashboard. */
public class DashboardRepository {

    private final ApiClient api;

    public DashboardRepository(ApiClient api) {
        this.api = api;
    }

    public DashboardStats load() throws ApiException {
        return DashboardStats.fromJson(api.getObject("/api/v1/dashboard"));
    }
}
