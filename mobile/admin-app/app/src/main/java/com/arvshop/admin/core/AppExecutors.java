package com.arvshop.admin.core;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Global thread pools — never run network/disk on the main thread. */
public final class AppExecutors {

    private static final AppExecutors INSTANCE = new AppExecutors();

    private final ExecutorService network = Executors.newFixedThreadPool(4);
    private final ExecutorService disk = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private AppExecutors() { }

    public static AppExecutors get() {
        return INSTANCE;
    }

    public ExecutorService network() {
        return network;
    }

    public ExecutorService disk() {
        return disk;
    }

    public void mainThread(Runnable r) {
        main.post(r);
    }
}
