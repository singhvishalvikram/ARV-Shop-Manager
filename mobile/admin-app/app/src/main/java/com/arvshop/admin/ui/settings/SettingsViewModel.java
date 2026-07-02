package com.arvshop.admin.ui.settings;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.arvshop.admin.core.AppExecutors;
import com.arvshop.admin.core.Result;
import com.arvshop.admin.core.ServiceLocator;
import com.arvshop.admin.data.remote.ApiException;

import java.util.Map;

public class SettingsViewModel extends ViewModel {

    private final MutableLiveData<Result<Map<String, String>>> loadState = new MutableLiveData<>();
    private final MutableLiveData<Result<Boolean>> saveState = new MutableLiveData<>();

    public LiveData<Result<Map<String, String>>> loadState() {
        return loadState;
    }

    public LiveData<Result<Boolean>> saveState() {
        return saveState;
    }

    public void load() {
        loadState.setValue(Result.loading());
        AppExecutors.get().network().execute(() -> {
            try {
                loadState.postValue(Result.success(ServiceLocator.settings().load()));
            } catch (ApiException e) {
                loadState.postValue(Result.error(e.getMessage(), e.code));
            }
        });
    }

    public void save(Map<String, String> changed) {
        saveState.setValue(Result.loading());
        AppExecutors.get().network().execute(() -> {
            try {
                ServiceLocator.settings().save(changed);
                saveState.postValue(Result.success(true));
            } catch (ApiException e) {
                saveState.postValue(Result.error(e.getMessage(), e.code));
            }
        });
    }
}
