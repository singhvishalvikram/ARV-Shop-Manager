package com.arvshop.admin.ui.dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.arvshop.admin.R;
import com.arvshop.admin.core.ServiceLocator;
import com.arvshop.admin.data.model.DashboardStats;
import com.arvshop.admin.ui.auth.LoginActivity;
import com.arvshop.admin.ui.inventory.InventoryListActivity;
import com.arvshop.admin.ui.settings.SettingsActivity;
import com.arvshop.admin.util.Money;
import com.google.android.material.snackbar.Snackbar;

/** Home screen: shop numbers + navigation to inventory/settings. */
public class DashboardActivity extends AppCompatActivity {

    private DashboardViewModel viewModel;
    private SwipeRefreshLayout swipeRefresh;
    private TextView totalItems, todayRevenue, stockValue, stockMrp, lowStock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_dashboard);
            getSupportActionBar().setSubtitle(ServiceLocator.auth().ownerName());
        }

        swipeRefresh = findViewById(R.id.swipe_refresh);
        totalItems = findViewById(R.id.stat_total_items);
        todayRevenue = findViewById(R.id.stat_today_revenue);
        stockValue = findViewById(R.id.stat_stock_value);
        stockMrp = findViewById(R.id.stat_stock_mrp);
        lowStock = findViewById(R.id.stat_low_stock);

        findViewById(R.id.btn_inventory).setOnClickListener(v ->
                startActivity(new Intent(this, InventoryListActivity.class)));
        findViewById(R.id.btn_sales_history).setOnClickListener(v ->
                startActivity(new Intent(this,
                        com.arvshop.admin.ui.sales.SalesHistoryActivity.class)));

        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        swipeRefresh.setOnRefreshListener(viewModel::load);

        // Global 401 handling: any expired-session response bounces to login.
        ServiceLocator.api().setUnauthorizedListener(() ->
                runOnUiThread(this::forceLogout));

        viewModel.state().observe(this, result -> {
            if (result == null) return;
            switch (result.status) {
                case LOADING:
                    swipeRefresh.setRefreshing(true);
                    break;
                case SUCCESS:
                    swipeRefresh.setRefreshing(false);
                    render(result.data);
                    break;
                case ERROR:
                    swipeRefresh.setRefreshing(false);
                    Snackbar.make(swipeRefresh, result.message, Snackbar.LENGTH_LONG).show();
                    break;
            }
        });
    }

    private void render(DashboardStats s) {
        totalItems.setText(String.valueOf(s.totalItems));
        todayRevenue.setText(Money.format(s.todayRevenue));
        stockValue.setText(Money.format(s.stockValue));
        stockMrp.setText(Money.format(s.stockMrp));
        int low = 0;
        StringBuilder sb = new StringBuilder();
        for (DashboardStats.LowStock item : s.recentItems) {
            if (item.quantity <= 5) {
                low++;
                sb.append("• ").append(item.name).append(" (").append(item.quantity).append(")\n");
            }
        }
        lowStock.setText(low == 0 ? getString(R.string.no_low_stock) : sb.toString().trim());
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.load();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_dashboard, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (id == R.id.action_logout) {
            com.arvshop.admin.core.AppExecutors.get().network().execute(() -> {
                ServiceLocator.auth().logout();
                runOnUiThread(this::forceLogout);
            });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void forceLogout() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
