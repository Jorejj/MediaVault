package com.example.mediavault;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AppExecutor {
    private static final Object LOCK = new Object();
    private static volatile AppExecutor sInstance;
    private final Executor diskIO;
    private final Executor networkIO;
    private final Executor mainThread;

    private AppExecutor(Executor diskIO, Executor networkIO, Executor mainThread) {
        this.diskIO = diskIO;
        this.networkIO = networkIO;
        this.mainThread = mainThread;
    }

    public static AppExecutor getInstance() {
        AppExecutor instance = sInstance;
        if (instance == null) {
            synchronized (LOCK) {
                instance = sInstance;
                if (instance == null) {
                    instance = new AppExecutor(
                            Executors.newSingleThreadExecutor(),
                            Executors.newFixedThreadPool(3),
                            new MainThreadExecutor()
                    );
                    sInstance = instance;
                }
            }
        }
        return instance;
    }

    public Executor diskIO() {
        return diskIO;
    }

    public Executor networkIO() {
        return networkIO;
    }

    public Executor mainThread() {
        return mainThread;
    }

    /** @deprecated Use getInstance().diskIO().execute() */
    @Deprecated
    public static void executeDb(Runnable task) {
        getInstance().diskIO().execute(task);
    }

    /** @deprecated Use getInstance().mainThread().execute() */
    @Deprecated
    public static void runOnMain(Runnable task) {
        getInstance().mainThread().execute(task);
    }

    private static class MainThreadExecutor implements Executor {
        private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable command) {
            mainThreadHandler.post(command);
        }
    }
}
