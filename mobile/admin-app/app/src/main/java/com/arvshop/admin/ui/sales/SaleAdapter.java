package com.arvshop.admin.ui.sales;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.arvshop.admin.R;
import com.arvshop.admin.data.model.Sale;
import com.arvshop.admin.util.Money;

/** Read-only list of recorded sales. */
public class SaleAdapter extends ListAdapter<Sale, SaleAdapter.Holder> {

    private static final DiffUtil.ItemCallback<Sale> DIFF =
            new DiffUtil.ItemCallback<Sale>() {
                @Override
                public boolean areItemsTheSame(@NonNull Sale a, @NonNull Sale b) {
                    return a.id == b.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull Sale a, @NonNull Sale b) {
                    return a.quantitySold == b.quantitySold && a.salePrice == b.salePrice;
                }
            };

    public SaleAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sale_row, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Sale s = getItem(position);
        h.name.setText(s.itemName.isEmpty() ? h.itemView.getContext().getString(R.string.sale_unknown_item) : s.itemName);
        h.meta.setText(h.itemView.getContext().getString(
                R.string.sales_row_meta, s.quantitySold, Money.format(s.salePrice), s.saleDate));
        h.total.setText(Money.format(s.lineTotal()));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView name, meta, total;

        Holder(View v) {
            super(v);
            name = v.findViewById(R.id.sale_name);
            meta = v.findViewById(R.id.sale_meta);
            total = v.findViewById(R.id.sale_total);
        }
    }
}
