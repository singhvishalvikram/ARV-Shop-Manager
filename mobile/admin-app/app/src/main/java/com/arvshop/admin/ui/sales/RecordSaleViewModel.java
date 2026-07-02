package com.arvshop.admin.ui.sales;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.arvshop.admin.core.AppExecutors;
import com.arvshop.admin.core.Result;
import com.arvshop.admin.core.ServiceLocator;
import com.arvshop.admin.data.remote.ApiException;

public class RecordSaleViewModel extends ViewModel {

    public static final String CODE_INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";

    private final MutableLiveData<Result<Boolean>> state = new MutableLiveData<>();

    public LiveData<Result<Boolean>> state() {
        return state;
    }

    public void record(long itemId, int quantity, double price, String description) {
        state.setValue(Result.loading());
        AppExecutors.get().network().execute(() -> {
            try {
                ServiceLocator.sales().recordSale(itemId, quantity, price, description);
                state.postValue(Result.success(true));
            } catch (ApiException e) {
                state.postValue(Result.error(e.getMessage(), e.code));
            }
        });
    }
}
