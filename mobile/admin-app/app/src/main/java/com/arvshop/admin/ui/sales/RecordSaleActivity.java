package com.arvshop.admin.ui.sales;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.arvshop.admin.R;
import com.arvshop.admin.util.ItemValidator;
import com.arvshop.admin.util.Money;
import com.google.android.material.snackbar.Snackbar;

/** Record a sale for a specific item. Server decrements stock and rejects overselling. */
public class RecordSaleActivity extends AppCompatActivity {

    private static final String EXTRA_ID = "item_id";
    private static final String EXTRA_NAME = "item_name";
    private static final String EXTRA_PRICE = "item_price";
    private static final String EXTRA_STOCK = "item_stock";

    public static Intent newIntent(Context ctx, long id, String name, double price, int stock) {
        return new Intent(ctx, RecordSaleActivity.class)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_PRICE, price)
                .putExtra(EXTRA_STOCK, stock);
    }

    private RecordSaleViewModel viewModel;
    private EditText qtyInput, priceInput;
    private Button recordButton;
    private ProgressBar progress;
    private long itemId;
    private int stock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_sale);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_record_sale);
        }

        itemId = getIntent().getLongExtra(EXTRA_ID, -1);
        String name = getIntent().getStringExtra(EXTRA_NAME);
        double price = getIntent().getDoubleExtra(EXTRA_PRICE, 0);
        stock = getIntent().getIntExtra(EXTRA_STOCK, 0);

        TextView itemLabel = findViewById(R.id.sale_item);
        itemLabel.setText(getString(R.string.sale_item_label, name, stock));
        qtyInput = findViewById(R.id.sale_qty);
        priceInput = findViewById(R.id.sale_price);
        priceInput.setText(price == Math.floor(price) ? String.valueOf((long) price) : String.valueOf(price));
        qtyInput.setText("1");
        recordButton = findViewById(R.id.sale_record);
        progress = findViewById(R.id.sale_progress);

        viewModel = new ViewModelProvider(this).get(RecordSaleViewModel.class);
        recordButton.setOnClickListener(v -> record());

        viewModel.state().observe(this, result -> {
            if (result == null) return;
            switch (result.status) {
                case LOADING:
                    setLoading(true);
                    break;
                case SUCCESS:
                    setLoading(false);
                    Snackbar.make(recordButton, R.string.sale_recorded, Snackbar.LENGTH_SHORT).show();
                    recordButton.postDelayed(this::finish, 700);
                    break;
                case ERROR:
                    setLoading(false);
                    String msg = RecordSaleViewModel.CODE_INSUFFICIENT_STOCK.equals(result.code)
                            ? getString(R.string.sale_insufficient_stock) : result.message;
                    Snackbar.make(recordButton, msg, Snackbar.LENGTH_LONG).show();
                    break;
            }
        });
    }

    private void record() {
        Integer qty = ItemValidator.parseNonNegativeInt(qtyInput.getText().toString());
        Double price = ItemValidator.parsePositiveDouble(priceInput.getText().toString());
        if (qty == null || qty <= 0) {
            qtyInput.setError(getString(R.string.sale_qty_error));
            return;
        }
        if (price == null || price < 0) {
            priceInput.setError(getString(R.string.sale_price_error));
            return;
        }
        viewModel.record(itemId, qty, price, "");
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        recordButton.setEnabled(!loading);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
