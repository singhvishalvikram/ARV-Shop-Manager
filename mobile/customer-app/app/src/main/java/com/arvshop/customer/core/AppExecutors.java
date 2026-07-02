package com.arvshop.customer.core;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Global thread pools. Network and disk work must never run on the main thread;
 * UI updates must always come back through {@link #mainThread()}.
 */
public final class AppExecutors {

    private static final AppExecutors INSTANCE = new AppExecutors();

    private final ExecutorService networkIo = Executors.newFixedThreadPool(4);
    private final ExecutorService diskIo = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AppExecutors() { }

    public static AppExecutors get() {
        return INSTANCE;
    }

    public ExecutorService network() {
        return networkIo;
    }

    public ExecutorService disk() {
        return diskIo;
    }

    public void mainThread(Runnable r) {
        mainHandler.post(r);
    }
}
