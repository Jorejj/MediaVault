package com.example.mediavault.receiver;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.mediavault.DailyGoalsManager;
import com.example.mediavault.DailyProgress;
import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.MainActivity;
import com.example.mediavault.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DailyGoalReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "daily_goals_channel";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {
        DailyGoalsManager goalsManager = new DailyGoalsManager(context);
        
        // Only notify if user has set at least one goal
        if (!goalsManager.hasAnyGoalSet()) {
            return;
        }

        DatabaseHelper dbHelper = new DatabaseHelper(context);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        DailyProgress progress = dbHelper.getDailyProgress(today);

        boolean goalsMet = true;
        StringBuilder message = new StringBuilder("Remaining today: ");
        int pendingCount = 0;

        if (goalsManager.getGoalPages() > 0 && progress.pagesRead < goalsManager.getGoalPages()) {
            goalsMet = false;
            message.append(goalsManager.getGoalPages() - progress.pagesRead).append(" pages");
            pendingCount++;
        }

        if (goalsManager.getGoalEpisodes() > 0 && progress.episodesWatched < goalsManager.getGoalEpisodes()) {
            goalsMet = false;
            if (pendingCount > 0) message.append(", ");
            message.append(goalsManager.getGoalEpisodes() - progress.episodesWatched).append(" eps");
            pendingCount++;
        }

        if (goalsManager.getGoalMinutes() > 0 && progress.minutesWatched < goalsManager.getGoalMinutes()) {
            goalsMet = false;
            if (pendingCount > 0) message.append(", ");
            message.append(goalsManager.getGoalMinutes() - progress.minutesWatched).append(" min");
        }

        if (!goalsMet) {
            sendNotification(context, "Daily Goal Reminder", message.toString());
        }
    }

    private void sendNotification(Context context, String title, String message) {
        createNotificationChannel(context);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.mediavault_logo) // Ensure this resource exists
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Daily Goals";
            String description = "Reminders for daily media consumption goals";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}
