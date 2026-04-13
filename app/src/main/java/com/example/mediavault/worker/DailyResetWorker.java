package com.example.mediavault.worker;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.mediavault.AppExecutor;
import com.example.mediavault.DailyGoalsManager;
import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.cloud.sync.SupabaseMediaSyncManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;

public class DailyResetWorker extends Worker {
    private static final String TAG = "DailyResetWorker";
    private static final String WORK_NAME = "daily_reset_sweep";

    public DailyResetWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "QA Sweep: Executing Gap-Filling & Streak Evaluation via AppExecutor");
        
        final CountDownLatch latch = new CountDownLatch(1);
        final Context context = getApplicationContext();

        AppExecutor.getInstance().diskIO().execute(() -> {
            try {
                DailyGoalsManager manager = DailyGoalsManager.getInstance(context);
                DatabaseHelper dbHelper = DatabaseHelper.getInstance(context);
                
                // 1. Retroactive Sweep (QA Absence Test)
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Calendar cal = Calendar.getInstance();
                
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                for (int i = 0; i < 7; i++) {
                    String dateStr = sdf.format(cal.getTime());
                    db.execSQL("INSERT OR IGNORE INTO " + DatabaseHelper.TABLE_DAILY_METRICS + 
                            " (" + DatabaseHelper.COL_DAILY_DATE + ") VALUES (?)", new String[]{dateStr});
                    SupabaseMediaSyncManager.enqueueUpsertDailyMetrics(context, dateStr);
                    cal.add(Calendar.DATE, -1);
                }

                // 2. Synchronous Streak Recalculation
                manager.calculateCurrentStreakSync();
                
            } catch (Exception e) {
                Log.e(TAG, "Worker execution failed", e);
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Log.e(TAG, "Worker interrupted", e);
            return Result.retry();
        }

        return Result.success();
    }

    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                DailyResetWorker.class,
                6, TimeUnit.HOURS
        )
                .setConstraints(constraints)
                .addTag(TAG)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
        );
    }

    public static void runNow(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DailyResetWorker.class)
                .addTag(TAG + "_immediate")
                .build();
        WorkManager.getInstance(context).enqueue(request);
    }
}
