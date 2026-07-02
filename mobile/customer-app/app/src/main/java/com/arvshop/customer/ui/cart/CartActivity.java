package com.arvshop.customer.ui.cart;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arvshop.customer.R;
import com.arvshop.customer.core.Result;
import com.arvshop.customer.core.ServiceLocator;
import com.arvshop.customer.data.model.CartItem;
import com.arvshop.customer.data.model.Catalog;
import com.arvshop.customer.data.model.ShopSettings;
import com.arvshop.customer.util.CartCalculator;
import com.arvshop.customer.util.Formats;
import com.arvshop.customer.util.WhatsAppCheckout;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/**
 * Cart screen + WhatsApp checkout (Phases 07/08). Checkout opens WhatsApp with
 * the pre-filled enquiry — identical model to the PWA; no order backend exists.
 */
public class CartActivity extends AppCompatActivity implements CartAdapter.Callbacks {

    private CartViewModel viewModel;
    private CartAdapter adapter;
    private TextView totalView;
    private Button checkoutButton;
    private View emptyState;
    private List<CartItem> currentItems;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_cart);
        }

        totalView = findViewById(R.id.cart_total);
        checkoutButton = findViewById(R.id.checkout_whatsapp);
        emptyState = findViewById(R.id.cart_empty_state);

        ShopSettings settings = currentSettings();
        String currency = settings != null ? settings.currencySymbol() : "₹";

        adapter = new CartAdapter(this, currency);
        RecyclerView list = findViewById(R.id.cart_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(CartViewModel.class);
        viewModel.items().observe(this, items -> {
            currentItems = items;
            adapter.submitList(items);
            boolean empty = items == null || items.isEmpty();
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            checkoutButton.setEnabled(!empty);
            totalView.setText(getString(R.string.cart_total,
                    Formats.money(currency, CartCalculator.total(items))));
        });

        checkoutButton.setOnClickListener(v -> checkout());
    }

    private ShopSettings currentSettings() {
        Result<Catalog> r = ServiceLocator.catalog().catalog().getValue();
        return (r != null && r.data != null) ? r.data.settings : null;
    }

    private void checkout() {
        if (currentItems == null || currentItems.isEmpty()) return;
        ShopSettings settings = currentSettings();
        if (settings == null || settings.whatsappNumber().isEmpty()) {
            Snackbar.make(checkoutButton, R.string.error_no_whatsapp_number,
                    Snackbar.LENGTH_LONG).show();
            return;
        }
        String message = WhatsAppCheckout.buildMessage(currentItems,
                settings.appTitle(), settings.currencySymbol());
        String url = WhatsAppCheckout.buildUrl(settings.whatsappNumber(), message);
        try {
            // wa.me resolves to the WhatsApp app when installed, browser otherwise.
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Snackbar.make(checkoutButton, R.string.error_whatsapp_open_failed,
                    Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onQtyChange(long productId, int newQty) {
        viewModel.setQty(productId, newQty);
    }

    @Override
    public void onRemove(long productId) {
        viewModel.remove(productId);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
