package com.arvshop.admin.ui.inventory;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.arvshop.admin.core.AppExecutors;
import com.arvshop.admin.core.Result;
import com.arvshop.admin.core.ServiceLocator;
import com.arvshop.admin.data.model.Item;
import com.arvshop.admin.data.remote.ApiException;

import java.util.List;

public class InventoryViewModel extends ViewModel {

    private final MutableLiveData<Result<List<Item>>> items = new MutableLiveData<>();
    private final MutableLiveData<Integer> pendingSync = new MutableLiveData<>(0);
    private String search = "";

    public LiveData<Result<List<Item>>> items() {
        return items;
    }

    public LiveData<Integer> pendingSync() {
        return pendingSync;
    }

    public void setSearch(String query) {
        this.search = query == null ? "" : query;
    }

    public void load() {
        items.setValue(Result.loading());
        AppExecutors.get().network().execute(() -> {
            // Opportunistically flush any offline-created items first.
            int synced = ServiceLocator.inventory().flushQueue();
            if (synced > 0) pendingSync.postValue(ServiceLocator.inventory().pendingCount());
            try {
                List<Item> list = ServiceLocator.inventory().list(search);
                items.postValue(Result.success(list));
            } catch (ApiException e) {
                items.postValue(Result.error(e.getMessage(), e.code));
            }
            pendingSync.postValue(ServiceLocator.inventory().pendingCount());
        });
    }

    public void delete(long id, Runnable onDone, DeleteError onError) {
        AppExecutors.get().network().execute(() -> {
            try {
                ServiceLocator.inventory().delete(id);
                AppExecutors.get().mainThread(onDone);
            } catch (ApiException e) {
                AppExecutors.get().mainThread(() -> onError.onError(e.getMessage()));
            }
        });
    }

    public interface DeleteError {
        void onError(String message);
    }
}
