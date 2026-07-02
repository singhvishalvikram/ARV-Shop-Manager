package com.arvshop.admin.ui.dashboard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.arvshop.admin.core.AppExecutors;
import com.arvshop.admin.core.Result;
import com.arvshop.admin.core.ServiceLocator;
import com.arvshop.admin.data.model.DashboardStats;
import com.arvshop.admin.data.remote.ApiException;

public class DashboardViewModel extends ViewModel {

    private final MutableLiveData<Result<DashboardStats>> state = new MutableLiveData<>();

    public LiveData<Result<DashboardStats>> state() {
        return state;
    }

    public void load() {
        state.setValue(Result.loading());
        AppExecutors.get().network().execute(() -> {
            try {
                state.postValue(Result.success(ServiceLocator.dashboard().load()));
            } catch (ApiException e) {
                state.postValue(Result.error(e.getMessage(), e.code));
            }
        });
    }
}
