package com.arvshop.customer.ui.catalog;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.arvshop.customer.R;
import com.arvshop.customer.core.Result;
import com.arvshop.customer.data.model.Catalog;
import com.arvshop.customer.data.model.Category;
import com.arvshop.customer.data.model.Product;
import com.arvshop.customer.ui.cart.CartActivity;
import com.arvshop.customer.ui.cart.CartViewModel;
import com.arvshop.customer.ui.detail.ProductDetailActivity;
import com.arvshop.customer.util.CartCalculator;
import com.arvshop.customer.util.CatalogFilter;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;

/**
 * Catalog screen (Phases 04/05/09/11/12): grid of products, search, category
 * chips, pull-to-refresh (version.json-gated), offline/error/empty states.
 */
public class MainActivity extends AppCompatActivity {

    private CatalogViewModel viewModel;
    private CartViewModel cartViewModel;
    private ProductAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private View emptyState;
    private TextView emptyText;
    private View retryButton;
    private EditText searchInput;
    private ChipGroup categoryChips;
    private int cartCount = 0;
    private long shownCatalogVersion = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        swipeRefresh = findViewById(R.id.swipe_refresh);
        emptyState = findViewById(R.id.empty_state);
        emptyText = findViewById(R.id.empty_text);
        retryButton = findViewById(R.id.retry_button);
        searchInput = findViewById(R.id.search_input);
        categoryChips = findViewById(R.id.category_chips);

        adapter = new ProductAdapter(this::openDetail);
        RecyclerView list = findViewById(R.id.product_list);
        list.setLayoutManager(new GridLayoutManager(this, 2));
        list.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(CatalogViewModel.class);
        cartViewModel = new ViewModelProvider(this).get(CartViewModel.class);

        swipeRefresh.setOnRefreshListener(viewModel::refresh);
        retryButton.setOnClickListener(v -> viewModel.retry());

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                viewModel.setQuery(s.toString());
            }
        });

        viewModel.refreshing().observe(this, r ->
                swipeRefresh.setRefreshing(Boolean.TRUE.equals(r)));
        viewModel.catalog().observe(this, this::renderCatalog);
        viewModel.filteredProducts().observe(this, products -> {
            adapter.submitList(products);
            boolean empty = products == null || products.isEmpty();
            Result<Catalog> r = viewModel.catalog().getValue();
            boolean loaded = r != null && r.data != null;
            emptyState.setVisibility(empty && loaded ? View.VISIBLE : View.GONE);
            if (empty && loaded) {
                emptyText.setText(R.string.empty_no_matches);
                retryButton.setVisibility(View.GONE);
            }
        });
        cartViewModel.items().observe(this, items -> {
            cartCount = CartCalculator.itemCount(items);
            invalidateOptionsMenu();
        });
    }

    private void renderCatalog(Result<Catalog> result) {
        if (result == null) return;
        switch (result.status) {
            case LOADING:
                break;
            case SUCCESS:
                Catalog c = result.data;
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(c.settings.appTitle());
                    getSupportActionBar().setSubtitle(c.settings.appSubtitle());
                }
                searchInput.setVisibility(c.settings.showSearch() ? View.VISIBLE : View.GONE);
                adapter.setSettings(c.settings);
                buildCategoryChips(c);
                if (!c.fromCache && shownCatalogVersion != -1
                        && c.version != shownCatalogVersion) {
                    Snackbar.make(swipeRefresh, R.string.catalog_updated,
                            Snackbar.LENGTH_SHORT).show();
                }
                shownCatalogVersion = c.version;
                break;
            case ERROR:
                if (result.data != null) {
                    // Stale cache still shown — just tell the user we're offline.
                    Snackbar.make(swipeRefresh, R.string.offline_showing_cache,
                            Snackbar.LENGTH_LONG).show();
                } else {
                    emptyState.setVisibility(View.VISIBLE);
                    emptyText.setText(R.string.error_no_connection);
                    retryButton.setVisibility(View.VISIBLE);
                }
                break;
        }
    }

    private void buildCategoryChips(Catalog catalog) {
        // G1 fix: fall back to distinct product types when categories.json is empty,
        // matching the production PWA (git-pages/app.js).
        java.util.List<Category> categories =
                com.arvshop.customer.util.Categories.effective(catalog.categories, catalog.products);
        if (!catalog.settings.showCategoryFilter() || categories.size() <= 1) {
            categoryChips.setVisibility(View.GONE);
            viewModel.setCategory(CatalogFilter.ALL_CATEGORIES);
            return;
        }
        categoryChips.setVisibility(View.VISIBLE);
        categoryChips.removeAllViews();
        String selected = viewModel.selectedCategory().getValue();
        addChip(getString(R.string.category_all), CatalogFilter.ALL_CATEGORIES,
                CatalogFilter.ALL_CATEGORIES.equals(selected));
        for (Category cat : categories) {
            addChip(cat.displayName, cat.name, cat.name.equals(selected));
        }
    }

    private void addChip(String label, String categoryValue, boolean checked) {
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setCheckable(true);
        chip.setChecked(checked);
        chip.setOnClickListener(v ->
                viewModel.setCategory(chip.isChecked() ? categoryValue
                        : CatalogFilter.ALL_CATEGORIES));
        categoryChips.addView(chip);
    }

    private void openDetail(Product product) {
        startActivity(ProductDetailActivity.newIntent(this, product));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem cart = menu.findItem(R.id.action_cart);
        cart.setTitle(cartCount > 0
                ? getString(R.string.menu_cart_count, cartCount)
                : getString(R.string.menu_cart));
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_cart) {
            startActivity(new Intent(this, CartActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Phase 09: cheap version.json check on foreground; full refetch only on change.
        viewModel.retry();
    }
}
