package com.example.mediavault;

import android.content.Context;
import android.content.SharedPreferences;

public class DailyGoalsManager {
    private static final String PREFS_NAME = "DailyGoalsPrefs";
    private static final String KEY_GOAL_PAGES = "goal_pages";
    private static final String KEY_GOAL_EPISODES = "goal_episodes";
    private static final String KEY_GOAL_MINUTES = "goal_minutes";
    private static final String KEY_REMINDER_HOUR = "reminder_hour";
    private static final String KEY_REMINDER_MINUTE = "reminder_minute";
    private static final String KEY_REMINDER_ENABLED = "reminder_enabled";

    private final SharedPreferences prefs;

    public DailyGoalsManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void setReminderTime(int hour, int minute) {
        prefs.edit()
            .putInt(KEY_REMINDER_HOUR, hour)
            .putInt(KEY_REMINDER_MINUTE, minute)
            .apply();
    }

    public int getReminderHour() {
        return prefs.getInt(KEY_REMINDER_HOUR, 20); // Default 8 PM
    }

    public int getReminderMinute() {
        return prefs.getInt(KEY_REMINDER_MINUTE, 0); // Default 00
    }

    public void setReminderEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply();
    }

    public boolean isReminderEnabled() {
        return prefs.getBoolean(KEY_REMINDER_ENABLED, true);
    }

    public void setGoalPages(int pages) {
        prefs.edit().putInt(KEY_GOAL_PAGES, pages).apply();
    }

    public int getGoalPages() {
        return prefs.getInt(KEY_GOAL_PAGES, 0); // Default 0 means no goal
    }

    public void setGoalEpisodes(int episodes) {
        prefs.edit().putInt(KEY_GOAL_EPISODES, episodes).apply();
    }

    public int getGoalEpisodes() {
        return prefs.getInt(KEY_GOAL_EPISODES, 0);
    }

    public void setGoalMinutes(int minutes) {
        prefs.edit().putInt(KEY_GOAL_MINUTES, minutes).apply();
    }

    public int getGoalMinutes() {
        return prefs.getInt(KEY_GOAL_MINUTES, 0);
    }

    public boolean hasAnyGoalSet() {
        return getGoalPages() > 0 || getGoalEpisodes() > 0 || getGoalMinutes() > 0;
    }
}
