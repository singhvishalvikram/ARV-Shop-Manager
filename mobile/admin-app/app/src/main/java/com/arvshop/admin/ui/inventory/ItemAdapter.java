package com.arvshop.admin.ui.inventory;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.arvshop.admin.R;
import com.arvshop.admin.data.model.Item;
import com.arvshop.admin.util.Money;

/** Inventory rows with quick actions (tap = edit, buttons = sell/delete). */
public class ItemAdapter extends ListAdapter<Item, ItemAdapter.Holder> {

    public interface Callbacks {
        void onEdit(Item item);
        void onSell(Item item);
        void onDelete(Item item);
    }

    private static final DiffUtil.ItemCallback<Item> DIFF =
            new DiffUtil.ItemCallback<Item>() {
                @Override
                public boolean areItemsTheSame(@NonNull Item a, @NonNull Item b) {
                    return a.id == b.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull Item a, @NonNull Item b) {
                    return a.quantity == b.quantity && a.price == b.price
                            && a.name.equals(b.name) && a.visible == b.visible
                            && a.featured == b.featured;
                }
            };

    private final Callbacks callbacks;

    public ItemAdapter(Callbacks callbacks) {
        super(DIFF);
        this.callbacks = callbacks;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_inventory_row, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Item item = getItem(position);
        h.name.setText(item.name);
        h.meta.setText(h.itemView.getContext().getString(
                R.string.item_meta, item.type, Money.format(item.price), item.quantity));

        boolean low = item.quantity <= 5;
        h.quantity.setText(String.valueOf(item.quantity));
        h.quantity.setBackgroundResource(item.quantity <= 0 ? R.drawable.bg_qty_zero
                : low ? R.drawable.bg_qty_low : R.drawable.bg_qty_ok);

        StringBuilder flags = new StringBuilder();
        if (!item.visible) flags.append("👁 hidden  ");
        if (item.featured) flags.append("★ featured  ");
        if (item.badge != null && !item.badge.isEmpty()) flags.append(item.badge);
        h.flags.setText(flags.toString().trim());
        h.flags.setVisibility(flags.length() == 0 ? View.GONE : View.VISIBLE);

        h.itemView.setOnClickListener(v -> callbacks.onEdit(item));
        h.sell.setOnClickListener(v -> callbacks.onSell(item));
        h.delete.setOnClickListener(v -> callbacks.onDelete(item));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView name, meta, quantity, flags;
        final View sell, delete;

        Holder(View v) {
            super(v);
            name = v.findViewById(R.id.row_name);
            meta = v.findViewById(R.id.row_meta);
            quantity = v.findViewById(R.id.row_qty);
            flags = v.findViewById(R.id.row_flags);
            sell = v.findViewById(R.id.row_sell);
            delete = v.findViewById(R.id.row_delete);
        }
    }
}
