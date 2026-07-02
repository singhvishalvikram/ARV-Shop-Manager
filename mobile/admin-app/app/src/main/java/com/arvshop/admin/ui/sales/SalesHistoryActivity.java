package com.arvshop.admin.ui.sales;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.arvshop.admin.R;
import com.arvshop.admin.data.model.SalesHistory;
import com.arvshop.admin.util.Money;
import com.google.android.material.snackbar.Snackbar;

/** Recent sales history + total revenue. */
public class SalesHistoryActivity extends AppCompatActivity {

    private SalesHistoryViewModel viewModel;
    private SaleAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private TextView summary;
    private View emptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sales_history);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_sales_history);
        }

        swipeRefresh = findViewById(R.id.swipe_refresh);
        summary = findViewById(R.id.sales_summary);
        emptyState = findViewById(R.id.empty_state);

        adapter = new SaleAdapter();
        RecyclerView list = findViewById(R.id.sales_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(SalesHistoryViewModel.class);
        swipeRefresh.setOnRefreshListener(viewModel::load);

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

        viewModel.load();
    }

    private void render(SalesHistory history) {
        adapter.submitList(history.sales);
        boolean empty = history.sales.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        summary.setText(getString(R.string.sales_summary,
                history.count, Money.format(history.totalRevenue)));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
