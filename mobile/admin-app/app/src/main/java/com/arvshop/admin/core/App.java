package com.arvshop.admin.core;

import android.app.Application;

/** Application entry point — initializes the service locator once. */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ServiceLocator.init(this);
    }
}
