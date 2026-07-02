package com.arvshop.customer;

import android.app.Application;

import com.arvshop.customer.core.ServiceLocator;

/** Application entry point — initializes the service locator once. */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ServiceLocator.init(this);
    }
}
