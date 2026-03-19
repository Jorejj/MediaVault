package com.example.mediavault;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppExecutor {
    private static final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private static final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    public static void executeDb(Runnable task) {
        databaseExecutor.execute(task);
    }

    public static void runOnMain(Runnable task) {
        mainThreadHandler.post(task);
    }
}