package com.arvshop.customer.ui.catalog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.arvshop.customer.R;
import com.arvshop.customer.core.ServiceLocator;
import com.arvshop.customer.data.model.Product;
import com.arvshop.customer.data.model.ShopSettings;
import com.arvshop.customer.util.Formats;

/**
 * Catalog grid adapter. DiffUtil keeps refreshes smooth (Phase 13); binding
 * honors settings toggles (show_mrp, show_discount_badges, show_images — Phase 11).
 */
public class ProductAdapter extends ListAdapter<Product, ProductAdapter.Holder> {

    public interface OnProductClick {
        void onClick(Product product);
    }

    private static final DiffUtil.ItemCallback<Product> DIFF =
            new DiffUtil.ItemCallback<Product>() {
                @Override
                public boolean areItemsTheSame(@NonNull Product a, @NonNull Product b) {
                    return a.id == b.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull Product a, @NonNull Product b) {
                    return a.equals(b);
                }
            };

    private final OnProductClick listener;
    private ShopSettings settings;

    public ProductAdapter(OnProductClick listener) {
        super(DIFF);
        this.listener = listener;
    }

    public void setSettings(ShopSettings settings) {
        this.settings = settings;
        notifyDataSetChanged(); // settings change is rare (one publish cycle)
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_product, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Product p = getItem(position);
        String currency = settings != null ? settings.currencySymbol() : "₹";
        boolean showMrp = settings == null || settings.showMrp();
        boolean showDiscount = settings == null || settings.showDiscountBadges();
        boolean showImages = settings == null || settings.showImages();

        h.name.setText(p.name);
        h.price.setText(Formats.money(currency, p.price));

        if (showMrp && p.hasDiscount()) {
            h.mrp.setVisibility(View.VISIBLE);
            h.mrp.setText(Formats.money(currency, p.mrp));
            h.mrp.setPaintFlags(h.mrp.getPaintFlags()
                    | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            h.mrp.setVisibility(View.GONE);
        }

        if (showDiscount && p.hasDiscount()) {
            h.discount.setVisibility(View.VISIBLE);
            h.discount.setText(h.itemView.getContext()
                    .getString(R.string.discount_off, p.discountPercent));
        } else {
            h.discount.setVisibility(View.GONE);
        }

        h.badge.setVisibility(p.hasBadge() ? View.VISIBLE : View.GONE);
        h.badge.setText(p.badge);

        h.outOfStock.setVisibility(p.inStock ? View.GONE : View.VISIBLE);
        h.itemView.setAlpha(p.inStock ? 1f : 0.55f);

        if (showImages) {
            h.image.setVisibility(View.VISIBLE);
            ServiceLocator.images().load(p.imageUrl, h.image);
        } else {
            h.image.setVisibility(View.GONE);
        }

        h.itemView.setOnClickListener(v -> listener.onClick(p));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView name;
        final TextView price;
        final TextView mrp;
        final TextView discount;
        final TextView badge;
        final TextView outOfStock;

        Holder(View v) {
            super(v);
            image = v.findViewById(R.id.product_image);
            name = v.findViewById(R.id.product_name);
            price = v.findViewById(R.id.product_price);
            mrp = v.findViewById(R.id.product_mrp);
            discount = v.findViewById(R.id.product_discount);
            badge = v.findViewById(R.id.product_badge);
            outOfStock = v.findViewById(R.id.product_out_of_stock);
        }
    }
}
