package com.arvshop.admin.ui.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.arvshop.admin.core.AppExecutors;
import com.arvshop.admin.core.Result;
import com.arvshop.admin.core.ServiceLocator;
import com.arvshop.admin.data.model.AuthUser;
import com.arvshop.admin.data.remote.ApiException;

/** Login state holder. */
public class AuthViewModel extends ViewModel {

    private final MutableLiveData<Result<AuthUser>> loginState = new MutableLiveData<>();

    public LiveData<Result<AuthUser>> loginState() {
        return loginState;
    }

    public boolean isLoggedIn() {
        return ServiceLocator.auth().isLoggedIn();
    }

    public void login(String phone, String password) {
        loginState.setValue(Result.loading());
        AppExecutors.get().network().execute(() -> {
            try {
                AuthUser user = ServiceLocator.auth().login(phone.trim(), password);
                loginState.postValue(Result.success(user));
            } catch (ApiException e) {
                loginState.postValue(Result.error(e.getMessage(), e.code));
            }
        });
    }
}
