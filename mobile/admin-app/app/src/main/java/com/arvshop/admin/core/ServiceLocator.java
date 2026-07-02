package com.arvshop.admin.core;

import android.content.Context;

import com.arvshop.admin.BuildConfig;
import com.arvshop.admin.data.local.OfflineQueue;
import com.arvshop.admin.data.local.SessionStore;
import com.arvshop.admin.data.remote.ApiClient;
import com.arvshop.admin.data.repo.AuthRepository;
import com.arvshop.admin.data.repo.DashboardRepository;
import com.arvshop.admin.data.repo.InventoryRepository;
import com.arvshop.admin.data.repo.SalesRepository;
import com.arvshop.admin.data.repo.SettingsRepository;

/** Manual DI. Single ApiClient shares the session across all repositories. */
public final class ServiceLocator {

    private static SessionStore session;
    private static ApiClient api;
    private static AuthRepository authRepository;
    private static InventoryRepository inventoryRepository;
    private static DashboardRepository dashboardRepository;
    private static SalesRepository salesRepository;
    private static SettingsRepository settingsRepository;

    private ServiceLocator() { }

    public static synchronized void init(Context appContext) {
        if (api != null) return;
        session = new SessionStore(appContext);
        api = new ApiClient(BuildConfig.API_BASE_URL, session);
        OfflineQueue queue = new OfflineQueue(appContext);
        authRepository = new AuthRepository(api, session);
        inventoryRepository = new InventoryRepository(api, queue);
        dashboardRepository = new DashboardRepository(api);
        salesRepository = new SalesRepository(api);
        settingsRepository = new SettingsRepository(api);
    }

    public static ApiClient api()                       { return api; }
    public static SessionStore session()                { return session; }
    public static AuthRepository auth()                 { return authRepository; }
    public static InventoryRepository inventory()       { return inventoryRepository; }
    public static DashboardRepository dashboard()       { return dashboardRepository; }
    public static SalesRepository sales()               { return salesRepository; }
    public static SettingsRepository settings()         { return settingsRepository; }
}
