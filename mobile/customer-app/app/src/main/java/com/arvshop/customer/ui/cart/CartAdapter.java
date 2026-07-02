package com.arvshop.customer.ui.cart;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.arvshop.customer.R;
import com.arvshop.customer.core.ServiceLocator;
import com.arvshop.customer.data.model.CartItem;
import com.arvshop.customer.util.Formats;

/** Cart line items with quantity steppers and remove. */
public class CartAdapter extends ListAdapter<CartItem, CartAdapter.Holder> {

    public interface Callbacks {
        void onQtyChange(long productId, int newQty);
        void onRemove(long productId);
    }

    private static final DiffUtil.ItemCallback<CartItem> DIFF =
            new DiffUtil.ItemCallback<CartItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull CartItem a, @NonNull CartItem b) {
                    return a.productId == b.productId;
                }

                @Override
                public boolean areContentsTheSame(@NonNull CartItem a, @NonNull CartItem b) {
                    return a.qty == b.qty && a.price == b.price && a.name.equals(b.name);
                }
            };

    private final Callbacks callbacks;
    private final String currency;

    public CartAdapter(Callbacks callbacks, String currency) {
        super(DIFF);
        this.callbacks = callbacks;
        this.currency = currency;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cart, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        CartItem it = getItem(position);
        h.name.setText(it.name);
        h.price.setText(Formats.money(currency, it.price));
        h.lineTotal.setText(Formats.money(currency, it.lineTotal()));
        h.qty.setText(String.valueOf(it.qty));
        ServiceLocator.images().load(it.imageUrl, h.image);
        h.plus.setOnClickListener(v -> callbacks.onQtyChange(it.productId, it.qty + 1));
        h.minus.setOnClickListener(v -> callbacks.onQtyChange(it.productId, it.qty - 1));
        h.remove.setOnClickListener(v -> callbacks.onRemove(it.productId));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView name;
        final TextView price;
        final TextView lineTotal;
        final TextView qty;
        final ImageButton plus;
        final ImageButton minus;
        final ImageButton remove;

        Holder(View v) {
            super(v);
            image = v.findViewById(R.id.cart_image);
            name = v.findViewById(R.id.cart_name);
            price = v.findViewById(R.id.cart_price);
            lineTotal = v.findViewById(R.id.cart_line_total);
            qty = v.findViewById(R.id.cart_qty);
            plus = v.findViewById(R.id.cart_qty_plus);
            minus = v.findViewById(R.id.cart_qty_minus);
            remove = v.findViewById(R.id.cart_remove);
        }
    }
}
