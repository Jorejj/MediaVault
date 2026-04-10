package com.example.mediavault.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.widget.RemoteViews;

import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.MainActivity;
import com.example.mediavault.R;
import com.example.mediavault.utils.ProgressValueUtils;

import java.util.Locale;

public class MediaVaultWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_WIDGET_QUICK_PLUS = "com.example.mediavault.ACTION_WIDGET_QUICK_PLUS";
    public static final String EXTRA_MEDIA_ID = "extra_media_id";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_WIDGET_QUICK_PLUS.equals(intent.getAction())) {
            int mediaId = intent.getIntExtra(EXTRA_MEDIA_ID, -1);
            if (mediaId > 0) {
                DatabaseHelper helper = DatabaseHelper.getInstance(context);
                helper.incrementProgressByOne(mediaId);
                refreshAllWidgets(context);
            }
        }
    }

    public static void refreshAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName componentName = new ComponentName(context, MediaVaultWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(componentName);
        for (int id : ids) {
            updateWidget(context, manager, id);
        }
    }

    private static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_media_vault);
        DatabaseHelper dbHelper = DatabaseHelper.getInstance(context);
        Cursor cursor = dbHelper.getHighestProgressOngoing();

        int mediaId = -1;
        if (cursor != null && cursor.moveToFirst()) {
            mediaId = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
            String title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TITLE));
            float progress = cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CURRENT_PROGRESS));
            int total = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_COUNT));
            String unit = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UNIT));
            float normalizedProgress = ProgressValueUtils.normalizeForUnit(progress, unit);

            views.setTextViewText(R.id.widget_title, title);
            views.setTextViewText(R.id.widget_progress_text,
                    String.format(Locale.getDefault(), "%s / %d", ProgressValueUtils.formatForDisplay(normalizedProgress, unit), total));
            views.setProgressBar(R.id.widget_progress_bar, Math.max(total, 1), Math.min(Math.round(normalizedProgress), total), false);
        } else {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_no_ongoing));
            views.setTextViewText(R.id.widget_progress_text, "0 / 0");
            views.setProgressBar(R.id.widget_progress_bar, 100, 0, false);
        }
        if (cursor != null) {
            cursor.close();
        }

        Intent openIntent = new Intent(context, MainActivity.class);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent);

        Intent plusIntent = new Intent(context, MediaVaultWidgetProvider.class);
        plusIntent.setAction(ACTION_WIDGET_QUICK_PLUS);
        plusIntent.putExtra(EXTRA_MEDIA_ID, mediaId);
        plusIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent plusPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId + 1000,
                plusIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_quick_plus, plusPendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
