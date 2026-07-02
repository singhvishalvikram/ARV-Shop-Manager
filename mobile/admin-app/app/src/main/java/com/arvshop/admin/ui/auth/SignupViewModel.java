package com.arvshop.admin.ui.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.arvshop.admin.core.AppExecutors;
import com.arvshop.admin.core.Result;
import com.arvshop.admin.core.ServiceLocator;
import com.arvshop.admin.data.model.AuthUser;
import com.arvshop.admin.data.remote.ApiException;

/** First-run owner account creation. */
public class SignupViewModel extends ViewModel {

    private final MutableLiveData<Result<AuthUser>> state = new MutableLiveData<>();

    public LiveData<Result<AuthUser>> state() {
        return state;
    }

    public void signup(String phone, String password, String name) {
        state.setValue(Result.loading());
        AppExecutors.get().network().execute(() -> {
            try {
                AuthUser user = ServiceLocator.auth().signup(phone.trim(), password, name.trim());
                state.postValue(Result.success(user));
            } catch (ApiException e) {
                state.postValue(Result.error(e.getMessage(), e.code));
            }
        });
    }
}
