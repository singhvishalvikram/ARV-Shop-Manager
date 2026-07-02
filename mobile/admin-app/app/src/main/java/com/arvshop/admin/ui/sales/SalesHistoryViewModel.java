package com.arvshop.admin.ui.sales;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.arvshop.admin.core.AppExecutors;
import com.arvshop.admin.core.Result;
import com.arvshop.admin.core.ServiceLocator;
import com.arvshop.admin.data.model.SalesHistory;
import com.arvshop.admin.data.remote.ApiException;

public class SalesHistoryViewModel extends ViewModel {

    private final MutableLiveData<Result<SalesHistory>> state = new MutableLiveData<>();

    public LiveData<Result<SalesHistory>> state() {
        return state;
    }

    public void load() {
        state.setValue(Result.loading());
        AppExecutors.get().network().execute(() -> {
            try {
                state.postValue(Result.success(ServiceLocator.sales().list()));
            } catch (ApiException e) {
                state.postValue(Result.error(e.getMessage(), e.code));
            }
        });
    }
}
