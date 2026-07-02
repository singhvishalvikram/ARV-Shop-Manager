package com.arvshop.customer.data.repo;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.arvshop.customer.core.AppExecutors;
import com.arvshop.customer.data.local.CartStore;
import com.arvshop.customer.data.model.CartItem;
import com.arvshop.customer.data.model.Product;

import java.util.ArrayList;
import java.util.List;

/**
 * Cart state holder over CartStore. All mutations run on the disk executor and
 * republish the full immutable list — the UI only ever observes.
 */
public class CartRepository {

    private final CartStore store;
    private final AppExecutors executors;
    private final MutableLiveData<List<CartItem>> items = new MutableLiveData<>();

    public CartRepository(CartStore store, AppExecutors executors) {
        this.store = store;
        this.executors = executors;
        executors.disk().execute(() -> items.postValue(store.load()));
    }

    public LiveData<List<CartItem>> items() {
        return items;
    }

    public void add(Product product, int qty) {
        mutate(list -> {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).productId == product.id) {
                    list.set(i, list.get(i).withQty(list.get(i).qty + qty));
                    return;
                }
            }
            list.add(new CartItem(product.id, product.name, product.price,
                    product.imageUrl, qty));
        });
    }

    public void setQty(long productId, int qty) {
        mutate(list -> {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).productId == productId) {
                    if (qty <= 0) {
                        list.remove(i);
                    } else {
                        list.set(i, list.get(i).withQty(qty));
                    }
                    return;
                }
            }
        });
    }

    public void remove(long productId) {
        setQty(productId, 0);
    }

    public void clear() {
        mutate(List::clear);
    }

    private interface CartMutation {
        void apply(List<CartItem> list);
    }

    private void mutate(CartMutation mutation) {
        executors.disk().execute(() -> {
            List<CartItem> list = new ArrayList<>(store.load());
            mutation.apply(list);
            store.save(list);
            items.postValue(list);
        });
    }
}
