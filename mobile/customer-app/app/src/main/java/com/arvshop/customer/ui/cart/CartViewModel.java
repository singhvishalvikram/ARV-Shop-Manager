package com.arvshop.customer.ui.cart;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.arvshop.customer.core.ServiceLocator;
import com.arvshop.customer.data.model.CartItem;
import com.arvshop.customer.data.repo.CartRepository;

import java.util.List;

/** Thin ViewModel over CartRepository for the cart screen and cart badge. */
public class CartViewModel extends ViewModel {

    private final CartRepository repo = ServiceLocator.cart();

    public LiveData<List<CartItem>> items() {
        return repo.items();
    }

    public void setQty(long productId, int qty) {
        repo.setQty(productId, qty);
    }

    public void remove(long productId) {
        repo.remove(productId);
    }

    public void clear() {
        repo.clear();
    }
}
