package com.example.mediavault.receiver;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.AlarmManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.mediavault.BuildConfig;
import com.example.mediavault.DailyGoalsManager;
import com.example.mediavault.DailyProgress;
import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.MainActivity;
import com.example.mediavault.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DailyGoalReminderReceiver extends BroadcastReceiver {

    public static final String ACTION_TEST_REMINDER = "com.example.mediavault.ACTION_TEST_DAILY_REMINDER";
    private static final String CHANNEL_ID_BASE = "daily_goals_channel";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {
        DailyGoalsManager goalsManager = DailyGoalsManager.getInstance(context);

        if (intent != null && ACTION_TEST_REMINDER.equals(intent.getAction())) {
            if (BuildConfig.DEBUG) {
                sendNotification(context, goalsManager, "Daily Goal Reminder (Test)", "This is a test reminder notification.");
            }
            return;
        }

        // Re-arm next reminder immediately so Android 12+/14 exact alarms keep chaining daily.
        scheduleNextReminder(context, goalsManager);
        
        // Only notify if user has set at least one goal
        if (!goalsManager.hasAnyGoalSet()) {
            return;
        }

        DatabaseHelper dbHelper = DatabaseHelper.getInstance(context);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        DailyProgress progress = dbHelper.getDailyProgress(today);

        boolean goalsMet = true;
        StringBuilder message = new StringBuilder("Remaining today: ");
        int pendingCount = 0;

        int pagesGoal = goalsManager.getGoalPages();
        int episodesGoal = goalsManager.getGoalEpisodes();
        int minutesGoal = goalsManager.getGoalMinutes();
        int pagesDone = Math.max(0, (int) Math.floor(progress.pagesRead));
        int episodesDone = Math.max(0, (int) Math.floor(progress.episodesWatched));
        int minutesDone = Math.max(0, Math.round(progress.minutesWatched));

        if (pagesGoal > 0 && pagesDone < pagesGoal) {
            goalsMet = false;
            message.append(pagesGoal - pagesDone).append(" pages");
            pendingCount++;
        }

        if (episodesGoal > 0 && episodesDone < episodesGoal) {
            goalsMet = false;
            if (pendingCount > 0) message.append(", ");
            message.append(episodesGoal - episodesDone).append(" eps");
            pendingCount++;
        }

        if (minutesGoal > 0 && minutesDone < minutesGoal) {
            goalsMet = false;
            if (pendingCount > 0) message.append(", ");
            message.append(minutesGoal - minutesDone).append(" min");
        }

        if (!goalsMet) {
            sendNotification(context, goalsManager, "Daily Goal Reminder", message.toString());
        }
    }

    public static String getChannelId(DailyGoalsManager goalsManager) {
        String intensity = goalsManager.getReminderVibrationIntensity();
        String soundFlag = goalsManager.isReminderSoundEnabled() ? "s1" : "s0";
        String vibrationFlag = goalsManager.isReminderVibrationEnabled() ? "v1" : "v0";
        return CHANNEL_ID_BASE + "_" + soundFlag + "_" + vibrationFlag + "_" + intensity;
    }

    public static void refreshNotificationChannel(Context context, DailyGoalsManager goalsManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager == null) return;

        String activeChannelId = getChannelId(goalsManager);
        createNotificationChannel(context, goalsManager, activeChannelId);

        for (NotificationChannel channel : notificationManager.getNotificationChannels()) {
            String id = channel.getId();
            if (id != null && id.startsWith(CHANNEL_ID_BASE) && !activeChannelId.equals(id)) {
                notificationManager.deleteNotificationChannel(id);
            }
        }
    }

    public static void scheduleNextReminder(Context context, DailyGoalsManager goalsManager) {
        if (context == null || goalsManager == null) return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, DailyGoalReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.cancel(pendingIntent);

        if (!goalsManager.isReminderEnabled() || !goalsManager.hasAnyGoalSet()) {
            return;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, goalsManager.getReminderHour());
        calendar.set(Calendar.MINUTE, goalsManager.getReminderMinute());
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        long triggerAt = calendar.getTimeInMillis();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }

    private void sendNotification(Context context, DailyGoalsManager goalsManager, String title, String message) {
        String channelId = getChannelId(goalsManager);
        createNotificationChannel(context, goalsManager, channelId);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.mediavault_logo) // Ensure this resource exists
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (goalsManager.isReminderSoundEnabled()) {
                builder.setDefaults(NotificationCompat.DEFAULT_SOUND);
            } else {
                builder.setSound(null);
            }

            if (goalsManager.isReminderVibrationEnabled()) {
                builder.setVibrate(goalsManager.getReminderVibrationPattern());
            } else {
                builder.setVibrate(null);
            }
        }

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private static void createNotificationChannel(Context context, DailyGoalsManager goalsManager, String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Daily Goals";
            String description = "Reminders for daily media consumption goals";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(channelId, name, importance);
            channel.setDescription(description);

            if (goalsManager.isReminderVibrationEnabled()) {
                channel.enableVibration(true);
                channel.setVibrationPattern(goalsManager.getReminderVibrationPattern());
            } else {
                channel.enableVibration(false);
                channel.setVibrationPattern(null);
            }

            if (goalsManager.isReminderSoundEnabled()) {
                Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                channel.setSound(soundUri, attributes);
            } else {
                channel.setSound(null, null);
            }
            
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}
