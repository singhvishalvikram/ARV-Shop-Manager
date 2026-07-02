package com.arvshop.customer.ui.detail;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.arvshop.customer.R;
import com.arvshop.customer.core.ServiceLocator;
import com.arvshop.customer.data.model.Product;
import com.arvshop.customer.util.Formats;
import com.google.android.material.snackbar.Snackbar;

/**
 * Product detail (Phase 06). The product is passed via intent extras (snapshot),
 * which keeps the screen working across process death without re-hitting the network.
 */
public class ProductDetailActivity extends AppCompatActivity {

    private static final String X_ID = "id";
    private static final String X_NAME = "name";
    private static final String X_DESC = "description";
    private static final String X_PRICE = "price";
    private static final String X_MRP = "mrp";
    private static final String X_DISCOUNT = "discount";
    private static final String X_IMAGE = "image";
    private static final String X_BADGE = "badge";
    private static final String X_CATEGORY = "category";
    private static final String X_IN_STOCK = "in_stock";
    private static final String X_FEATURED = "featured";
    private static final String X_SORT = "sort";

    public static Intent newIntent(Context ctx, Product p) {
        return new Intent(ctx, ProductDetailActivity.class)
                .putExtra(X_ID, p.id)
                .putExtra(X_NAME, p.name)
                .putExtra(X_DESC, p.description)
                .putExtra(X_PRICE, p.price)
                .putExtra(X_MRP, p.mrp)
                .putExtra(X_DISCOUNT, p.discountPercent)
                .putExtra(X_IMAGE, p.imageUrl)
                .putExtra(X_BADGE, p.badge)
                .putExtra(X_CATEGORY, p.category)
                .putExtra(X_IN_STOCK, p.inStock)
                .putExtra(X_FEATURED, p.featured)
                .putExtra(X_SORT, p.sortOrder);
    }

    private Product productFromIntent() {
        Intent i = getIntent();
        return new Product(
                i.getLongExtra(X_ID, -1),
                i.getStringExtra(X_NAME),
                i.getStringExtra(X_CATEGORY),
                i.getStringExtra(X_DESC),
                i.getDoubleExtra(X_PRICE, 0),
                i.getDoubleExtra(X_MRP, 0),
                i.getIntExtra(X_DISCOUNT, 0),
                i.getStringExtra(X_IMAGE),
                i.getBooleanExtra(X_FEATURED, false),
                i.getStringExtra(X_BADGE),
                i.getIntExtra(X_SORT, 0),
                i.getBooleanExtra(X_IN_STOCK, true));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        Product p = productFromIntent();
        if (p.id < 0) { // defensive: launched with a malformed intent
            finish();
            return;
        }

        ImageView image = findViewById(R.id.detail_image);
        TextView name = findViewById(R.id.detail_name);
        TextView price = findViewById(R.id.detail_price);
        TextView mrp = findViewById(R.id.detail_mrp);
        TextView discount = findViewById(R.id.detail_discount);
        TextView badge = findViewById(R.id.detail_badge);
        TextView description = findViewById(R.id.detail_description);
        TextView outOfStock = findViewById(R.id.detail_out_of_stock);
        Button addToCart = findViewById(R.id.add_to_cart);

        setTitle(p.name);
        name.setText(p.name);
        price.setText(Formats.money("₹", p.price));
        if (p.hasDiscount()) {
            mrp.setVisibility(View.VISIBLE);
            mrp.setText(Formats.money("₹", p.mrp));
            mrp.setPaintFlags(mrp.getPaintFlags()
                    | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            discount.setVisibility(View.VISIBLE);
            discount.setText(getString(R.string.discount_off, p.discountPercent));
        } else {
            mrp.setVisibility(View.GONE);
            discount.setVisibility(View.GONE);
        }
        badge.setVisibility(p.hasBadge() ? View.VISIBLE : View.GONE);
        badge.setText(p.badge);
        description.setText(p.description);
        description.setVisibility(p.description == null || p.description.isEmpty()
                ? View.GONE : View.VISIBLE);
        ServiceLocator.images().load(p.imageUrl, image);

        // AGENTS.md out-of-stock flow: dim + hide add-to-cart when unavailable.
        outOfStock.setVisibility(p.inStock ? View.GONE : View.VISIBLE);
        addToCart.setVisibility(p.inStock ? View.VISIBLE : View.GONE);
        addToCart.setOnClickListener(v -> {
            ServiceLocator.cart().add(p, 1);
            Snackbar.make(v, R.string.added_to_cart, Snackbar.LENGTH_SHORT).show();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
