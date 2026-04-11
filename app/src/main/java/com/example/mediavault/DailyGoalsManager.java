package com.example.mediavault;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DailyGoalsManager {
    private static final String TAG = "DailyGoalsManager";
    private static final String PREFS_NAME = "daily_goals_prefs";
    private static final String KEY_STREAK_COUNT = "streak_count";
    private static final String KEY_MANGA_GOAL = "daily_manga_goal"; // used for pages
    private static final String KEY_ANIME_GOAL = "daily_anime_goal"; // used for episodes
    private static final String KEY_MINUTES_GOAL = "daily_minutes_goal";
    private static final String KEY_REMINDER_HOUR = "reminder_hour";
    private static final String KEY_REMINDER_MINUTE = "reminder_minute";
    private static final String KEY_REMINDER_ENABLED = "reminder_enabled";
    private static final String KEY_REMINDER_SOUND_ENABLED = "reminder_sound_enabled";
    private static final String KEY_REMINDER_VIBRATION_ENABLED = "reminder_vibration_enabled";
    private static final String KEY_REMINDER_VIBRATION_INTENSITY = "reminder_vibration_intensity";

    private static DailyGoalsManager instance;
    private final Context context;
    private final DatabaseHelper dbHelper;
    private final SharedPreferences prefs;

    private DailyGoalsManager(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = DatabaseHelper.getInstance(this.context);
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized DailyGoalsManager getInstance(Context context) {
        if (instance == null) {
            instance = new DailyGoalsManager(context);
        }
        return instance;
    }

    // --- ASYNC METHODS (For UI/Accessibility Triggers) ---
    
    public void incrementMangaProgress() {
        AppExecutor.getInstance().diskIO().execute(this::incrementMangaProgressSync);
    }

    public void incrementAnimeProgress() {
        AppExecutor.getInstance().diskIO().execute(this::incrementAnimeProgressSync);
    }

    public void calculateCurrentStreak() {
        AppExecutor.getInstance().diskIO().execute(this::calculateCurrentStreakSync);
    }

    // --- SYNCHRONOUS METHODS (For WorkManager/Internal use) ---

    public void incrementMangaProgressSync() {
        try {
            String today = getTodayDate();
            ensureDailyRowExists(today);
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.execSQL("UPDATE " + DatabaseHelper.TABLE_DAILY_METRICS + 
                    " SET " + DatabaseHelper.COL_DAILY_CHAPTERS + " = " + DatabaseHelper.COL_DAILY_CHAPTERS + " + 1 " +
                    " WHERE " + DatabaseHelper.COL_DAILY_DATE + " = ?", new String[]{today});
            checkGoalMet(today);
        } catch (Exception e) {
            Log.e(TAG, "Sync Manga Increment Failed", e);
        }
    }

    public void incrementAnimeProgressSync() {
        try {
            String today = getTodayDate();
            ensureDailyRowExists(today);
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.execSQL("UPDATE " + DatabaseHelper.TABLE_DAILY_METRICS + 
                    " SET " + DatabaseHelper.COL_DAILY_EPISODES + " = " + DatabaseHelper.COL_DAILY_EPISODES + " + 1 " +
                    " WHERE " + DatabaseHelper.COL_DAILY_DATE + " = ?", new String[]{today});
            checkGoalMet(today);
        } catch (Exception e) {
            Log.e(TAG, "Sync Anime Increment Failed", e);
        }
    }

    public void calculateCurrentStreakSync() {
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            int streak = 0;
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

            while (true) {
                String dateStr = sdf.format(cal.getTime());
                try (Cursor cursor = db.rawQuery("SELECT " + DatabaseHelper.COL_DAILY_GOAL_MET + 
                        " FROM " + DatabaseHelper.TABLE_DAILY_METRICS + 
                        " WHERE " + DatabaseHelper.COL_DAILY_DATE + " = ?", new String[]{dateStr})) {
                    
                    if (cursor != null && cursor.moveToFirst()) {
                        if (cursor.getInt(0) == 1) {
                            streak++;
                            cal.add(Calendar.DATE, -1);
                        } else {
                            if (dateStr.equals(getTodayDate())) {
                                cal.add(Calendar.DATE, -1);
                                continue; 
                            }
                            break;
                        }
                    } else {
                        if (dateStr.equals(getTodayDate())) {
                            cal.add(Calendar.DATE, -1);
                            continue;
                        }
                        break;
                    }
                }
            }
            setStreakCount(streak);
        } catch (Exception e) {
            Log.e(TAG, "Sync Streak Calculation Failed", e);
        }
    }

    private void ensureDailyRowExists(String date) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.execSQL("INSERT OR IGNORE INTO " + DatabaseHelper.TABLE_DAILY_METRICS + 
                    " (" + DatabaseHelper.COL_DAILY_DATE + ") VALUES (?)", new String[]{date});
        } catch (SQLiteException e) {
            Log.e(TAG, "Error ensuring daily row", e);
        }
    }

    private void checkGoalMet(String date) {
        try {
            DailyProgress progress = dbHelper.getDailyProgress(date);

            int pageGoal = getGoalPages();
            int episodeGoal = getGoalEpisodes();
            int minuteGoal = getGoalMinutes();

            int normalizedPages = Math.max(0, (int) Math.floor(progress.pagesRead));
            int normalizedEpisodes = Math.max(0, (int) Math.floor(progress.episodesWatched));
            int normalizedMinutes = Math.max(0, Math.round(progress.minutesWatched));

            boolean hasGoalConfigured = false;
            boolean allConfiguredGoalsMet = true;
            if (pageGoal > 0) {
                hasGoalConfigured = true;
                if (normalizedPages < pageGoal) {
                    allConfiguredGoalsMet = false;
                }
            }
            if (episodeGoal > 0) {
                hasGoalConfigured = true;
                if (normalizedEpisodes < episodeGoal) {
                    allConfiguredGoalsMet = false;
                }
            }
            if (minuteGoal > 0) {
                hasGoalConfigured = true;
                if (normalizedMinutes < minuteGoal) {
                    allConfiguredGoalsMet = false;
                }
            }

            int goalMet = (hasGoalConfigured && allConfiguredGoalsMet) ? 1 : 0;

            SQLiteDatabase wdb = dbHelper.getWritableDatabase();
            wdb.execSQL("UPDATE " + DatabaseHelper.TABLE_DAILY_METRICS +
                    " SET " + DatabaseHelper.COL_DAILY_GOAL_MET + " = ? WHERE " +
                    DatabaseHelper.COL_DAILY_DATE + " = ?", new Object[]{goalMet, date});

            calculateCurrentStreakSync();
        } catch (SQLiteException e) {
            Log.e(TAG, "Error checking goal met", e);
        }
    }

    // --- GETTERS & SETTERS (SharedPreferences) ---

    public int getStreakCount() { return prefs.getInt(KEY_STREAK_COUNT, 0); }
    public void setStreakCount(int count) { prefs.edit().putInt(KEY_STREAK_COUNT, count).apply(); }
    
    public int getGoalPages() { return prefs.getInt(KEY_MANGA_GOAL, 0); }
    public void setGoalPages(int goal) {
        prefs.edit().putInt(KEY_MANGA_GOAL, goal).apply();
        recalculateTodayGoalStateAsync();
    }
    
    public int getGoalEpisodes() { return prefs.getInt(KEY_ANIME_GOAL, 0); }
    public void setGoalEpisodes(int goal) {
        prefs.edit().putInt(KEY_ANIME_GOAL, goal).apply();
        recalculateTodayGoalStateAsync();
    }
    
    public int getGoalMinutes() { return prefs.getInt(KEY_MINUTES_GOAL, 0); }
    public void setGoalMinutes(int goal) {
        prefs.edit().putInt(KEY_MINUTES_GOAL, goal).apply();
        recalculateTodayGoalStateAsync();
    }

    public boolean hasAnyGoalSet() {
        return getGoalPages() > 0 || getGoalEpisodes() > 0 || getGoalMinutes() > 0;
    }

    public boolean isReminderEnabled() { return prefs.getBoolean(KEY_REMINDER_ENABLED, false); }
    public void setReminderEnabled(boolean enabled) { prefs.edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply(); }

    public int getReminderHour() { return prefs.getInt(KEY_REMINDER_HOUR, 20); }
    public int getReminderMinute() { return prefs.getInt(KEY_REMINDER_MINUTE, 0); }
    public void setReminderTime(int hour, int minute) {
        prefs.edit().putInt(KEY_REMINDER_HOUR, hour).putInt(KEY_REMINDER_MINUTE, minute).apply();
    }

    public boolean isReminderSoundEnabled() {
        return prefs.getBoolean(KEY_REMINDER_SOUND_ENABLED, true);
    }

    public void setReminderSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_REMINDER_SOUND_ENABLED, enabled).apply();
    }

    public boolean isReminderVibrationEnabled() {
        return prefs.getBoolean(KEY_REMINDER_VIBRATION_ENABLED, true);
    }

    public void setReminderVibrationEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_REMINDER_VIBRATION_ENABLED, enabled).apply();
    }

    public String getReminderVibrationIntensity() {
        String value = prefs.getString(KEY_REMINDER_VIBRATION_INTENSITY, "medium");
        if ("low".equals(value) || "medium".equals(value) || "high".equals(value)) {
            return value;
        }
        return "medium";
    }

    public void setReminderVibrationIntensity(String intensity) {
        String normalized = intensity == null ? "medium" : intensity.trim().toLowerCase(Locale.US);
        if (!"low".equals(normalized) && !"medium".equals(normalized) && !"high".equals(normalized)) {
            normalized = "medium";
        }
        prefs.edit().putString(KEY_REMINDER_VIBRATION_INTENSITY, normalized).apply();
    }

    public long[] getReminderVibrationPattern() {
        if (!isReminderVibrationEnabled()) {
            return new long[]{0L};
        }
        String intensity = getReminderVibrationIntensity();
        switch (intensity) {
            case "low":
                return new long[]{0, 120};
            case "high":
                return new long[]{0, 220, 120, 220};
            case "medium":
            default:
                return new long[]{0, 180, 100, 180};
        }
    }

    private String getTodayDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date());
    }

    private void recalculateTodayGoalStateAsync() {
        AppExecutor.getInstance().diskIO().execute(() -> {
            String today = getTodayDate();
            ensureDailyRowExists(today);
            checkGoalMet(today);
        });
    }
}
