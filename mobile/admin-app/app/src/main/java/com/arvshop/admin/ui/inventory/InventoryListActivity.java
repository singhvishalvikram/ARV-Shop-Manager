package com.arvshop.admin.ui.inventory;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.arvshop.admin.R;
import com.arvshop.admin.data.model.Item;
import com.arvshop.admin.ui.item.ItemEditActivity;
import com.arvshop.admin.ui.sales.RecordSaleActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

/** Inventory browser with search, add (FAB), edit/sell/delete per row. */
public class InventoryListActivity extends AppCompatActivity implements ItemAdapter.Callbacks {

    private InventoryViewModel viewModel;
    private ItemAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private View emptyState;
    private TextView pendingBanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_inventory);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        swipeRefresh = findViewById(R.id.swipe_refresh);
        emptyState = findViewById(R.id.empty_state);
        pendingBanner = findViewById(R.id.pending_banner);
        EditText search = findViewById(R.id.search_input);

        adapter = new ItemAdapter(this);
        RecyclerView list = findViewById(R.id.item_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(InventoryViewModel.class);
        swipeRefresh.setOnRefreshListener(viewModel::load);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                viewModel.setSearch(s.toString());
                viewModel.load();
            }
        });

        FloatingActionButton fab = findViewById(R.id.fab_add);
        fab.setOnClickListener(v -> startActivity(new Intent(this, ItemEditActivity.class)));

        viewModel.items().observe(this, result -> {
            if (result == null) return;
            switch (result.status) {
                case LOADING:
                    swipeRefresh.setRefreshing(true);
                    break;
                case SUCCESS:
                    swipeRefresh.setRefreshing(false);
                    adapter.submitList(result.data);
                    emptyState.setVisibility(result.data.isEmpty() ? View.VISIBLE : View.GONE);
                    break;
                case ERROR:
                    swipeRefresh.setRefreshing(false);
                    Snackbar.make(swipeRefresh, result.message, Snackbar.LENGTH_LONG).show();
                    break;
            }
        });

        viewModel.pendingSync().observe(this, count -> {
            if (count != null && count > 0) {
                pendingBanner.setVisibility(View.VISIBLE);
                pendingBanner.setText(getString(R.string.pending_sync, count));
            } else {
                pendingBanner.setVisibility(View.GONE);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.load();
    }

    @Override
    public void onEdit(Item item) {
        startActivity(ItemEditActivity.editIntent(this, item.id));
    }

    @Override
    public void onSell(Item item) {
        startActivity(RecordSaleActivity.newIntent(this, item.id, item.name, item.price, item.quantity));
    }

    @Override
    public void onDelete(Item item) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(getString(R.string.delete_confirm_message, item.name))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) ->
                        viewModel.delete(item.id,
                                () -> {
                                    Snackbar.make(swipeRefresh, R.string.item_deleted,
                                            Snackbar.LENGTH_SHORT).show();
                                    viewModel.load();
                                },
                                message -> Snackbar.make(swipeRefresh, message,
                                        Snackbar.LENGTH_LONG).show()))
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
