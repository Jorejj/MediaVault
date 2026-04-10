package com.example.mediavault.worker;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.mediavault.AppExecutor;
import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.R;
import com.example.mediavault.widget.ToastUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;

public class GoalReminderWorker extends Worker {
    private static final String TAG = "GoalReminderWorker";
    private static final String CHANNEL_ID = "daily_goal_channel";

    public GoalReminderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Goal Reminder Worker: Checking today's goal status via AppExecutor");
        
        final AtomicBoolean goalMet = new AtomicBoolean(false);
        final DatabaseHelper dbHelper = DatabaseHelper.getInstance(getApplicationContext());
        final String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        final CountDownLatch latch = new CountDownLatch(1);
        try {
            AppExecutor.getInstance().diskIO().execute(() -> {
                try (SQLiteDatabase db = dbHelper.getReadableDatabase();
                     Cursor cursor = db.rawQuery("SELECT " + DatabaseHelper.COL_DAILY_GOAL_MET + 
                             " FROM " + DatabaseHelper.TABLE_DAILY_METRICS + 
                             " WHERE " + DatabaseHelper.COL_DAILY_DATE + " = ?", new String[]{today})) {
                    
                    if (cursor != null && cursor.moveToFirst()) {
                        goalMet.set(cursor.getInt(0) == 1);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "DB error in AppExecutor block", e);
                } finally {
                    latch.countDown();
                }
            });

            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.retry();
        }

        if (!goalMet.get()) {
            evaluateNotificationOrToast();
        }

        return Result.success();
    }

    private void evaluateNotificationOrToast() {
        boolean canNotify = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            canNotify = ContextCompat.checkSelfPermission(getApplicationContext(), 
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }

        if (canNotify) {
            triggerNotification();
        } else {
            AppExecutor.getInstance().mainThread().execute(() -> {
                ToastUtils.showCustomToast(getApplicationContext(), "Keep your streak alive! You haven't reached your media goal today.");
            });
        }
    }

    private void triggerNotification() {
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Daily Goal Reminder", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.mediavault_logo)
                .setContentTitle("Goal Progress Update")
                .setContentText("Keep your streak alive! You haven't reached your media goal today.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        notificationManager.notify(101, builder.build());
    }
}
