package com.example.mediavault.worker;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.mediavault.utils.AccessibilityServiceHelper;

import java.util.concurrent.TimeUnit;

/**
 * Periodic WorkManager job that checks if the accessibility service
 * is supposed to be running but isn't (e.g., after app update).
 * Shows a notification to remind user to re-enable if needed.
 */
public class ServiceStatusWorker extends Worker {

    private static final String TAG = "ServiceStatusWorker";
    private static final String WORK_NAME = "service_status_check";
    private static final String PREFS_NAME = "mediavault_prefs";
    private static final String KEY_LAST_NOTIFICATION = "last_service_notification";
    
    // Minimum time between notifications (6 hours)
    private static final long NOTIFICATION_COOLDOWN_MS = 6 * 60 * 60 * 1000;

    public ServiceStatusWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        
        // Check if user has tracking enabled in settings
        SharedPreferences settingsPrefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE);
        boolean trackingEnabled = settingsPrefs.getBoolean("external_tracking_enabled", false);
        
        if (!trackingEnabled) {
            // User doesn't want tracking, no need to check
            return Result.success();
        }
        
        // Check if accessibility service is running
        boolean serviceRunning = AccessibilityServiceHelper.isServiceEnabled(context);
        
        if (!serviceRunning) {
            // Service should be running but isn't
            // Check cooldown to avoid spamming notifications
            if (shouldShowNotification(context)) {
                AccessibilityServiceHelper.showServiceDisabledNotification(context);
                markNotificationShown(context);
            }
        } else {
            // Service is running, cancel any existing notification
            AccessibilityServiceHelper.cancelServiceDisabledNotification(context);
        }
        
        return Result.success();
    }

    private boolean shouldShowNotification(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastNotification = prefs.getLong(KEY_LAST_NOTIFICATION, 0);
        long now = System.currentTimeMillis();
        
        return (now - lastNotification) > NOTIFICATION_COOLDOWN_MS;
    }

    private void markNotificationShown(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_NOTIFICATION, System.currentTimeMillis()).apply();
    }

    /**
     * Schedule the periodic service status check.
     * Runs every 4 hours when device is not low battery.
     */
    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build();

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                ServiceStatusWorker.class,
                4, TimeUnit.HOURS
        )
                .setConstraints(constraints)
                .addTag(TAG)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
        );
    }

    /**
     * Cancel the periodic service status check.
     */
    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }
    
    /**
     * Run an immediate check (one-time)
     */
    public static void runNow(Context context) {
        androidx.work.OneTimeWorkRequest workRequest = new androidx.work.OneTimeWorkRequest.Builder(
                ServiceStatusWorker.class
        )
                .addTag(TAG + "_immediate")
                .build();
        
        WorkManager.getInstance(context).enqueue(workRequest);
    }
}
