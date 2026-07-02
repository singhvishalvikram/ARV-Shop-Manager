package com.arvshop.admin.ui.item;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.arvshop.admin.core.AppExecutors;
import com.arvshop.admin.core.Result;
import com.arvshop.admin.core.ServiceLocator;
import com.arvshop.admin.data.model.Item;
import com.arvshop.admin.data.remote.ApiException;

import org.json.JSONObject;

/** Add/edit item. Emits SUCCESS with null data to mean "saved offline". */
public class ItemEditViewModel extends ViewModel {

    private final MutableLiveData<Result<Item>> saveState = new MutableLiveData<>();
    private final MutableLiveData<Result<Item>> loadState = new MutableLiveData<>();

    public LiveData<Result<Item>> saveState() {
        return saveState;
    }

    public LiveData<Result<Item>> loadState() {
        return loadState;
    }

    public void loadItem(long id) {
        loadState.setValue(Result.loading());
        AppExecutors.get().network().execute(() -> {
            try {
                loadState.postValue(Result.success(ServiceLocator.inventory().get(id)));
            } catch (Exception e) {
                loadState.postValue(Result.error(e.getMessage(), null));
            }
        });
    }

    public void create(JSONObject payload, JSONObject merchandising) {
        saveState.setValue(Result.loading());
        AppExecutors.get().network().execute(() -> {
            try {
                Item item = ServiceLocator.inventory().create(payload, merchandising);
                saveState.postValue(Result.success(item)); // item==null → queued offline
            } catch (ApiException e) {
                saveState.postValue(Result.error(e.getMessage(), e.code));
            }
        });
    }

    public void update(long id, JSONObject payload) {
        saveState.setValue(Result.loading());
        AppExecutors.get().network().execute(() -> {
            try {
                saveState.postValue(Result.success(ServiceLocator.inventory().update(id, payload)));
            } catch (ApiException e) {
                saveState.postValue(Result.error(e.getMessage(), e.code));
            }
        });
    }
}
