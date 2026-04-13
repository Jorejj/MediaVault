package com.example.mediavault.cloud.auth;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.JsonObject;

public class SupabaseSessionManager {
    private static final String PREFS_NAME = "supabase_auth";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_EXPIRES_AT = "expires_at";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_DEBUG_SESSION = "debug_session";
    private static final String KEY_KEEP_SIGNED_IN = "keep_signed_in";

    private final SharedPreferences preferences;

    public SupabaseSessionManager(Context context) {
        this.preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveSession(JsonObject payload) {
        if (payload == null) return;
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(KEY_DEBUG_SESSION, false);
        if (payload.has("access_token") && !payload.get("access_token").isJsonNull()) {
            editor.putString(KEY_ACCESS_TOKEN, payload.get("access_token").getAsString());
        }
        if (payload.has("refresh_token") && !payload.get("refresh_token").isJsonNull()) {
            editor.putString(KEY_REFRESH_TOKEN, payload.get("refresh_token").getAsString());
        }
        if (payload.has("expires_at") && !payload.get("expires_at").isJsonNull()) {
            editor.putLong(KEY_EXPIRES_AT, payload.get("expires_at").getAsLong());
        }
        if (payload.has("user") && payload.get("user").isJsonObject()) {
            JsonObject user = payload.getAsJsonObject("user");
            if (user.has("id") && !user.get("id").isJsonNull()) {
                editor.putString(KEY_USER_ID, user.get("id").getAsString());
            }
            if (user.has("email") && !user.get("email").isJsonNull()) {
                editor.putString(KEY_EMAIL, user.get("email").getAsString());
            }
        }
        editor.apply();
    }

    public void saveDebugSession(String username) {
        String userValue = username == null || username.trim().isEmpty() ? "user123" : username.trim();
        preferences.edit()
                .putString(KEY_ACCESS_TOKEN, "debug-session-token")
                .putString(KEY_REFRESH_TOKEN, "")
                .putLong(KEY_EXPIRES_AT, Long.MAX_VALUE)
                .putString(KEY_USER_ID, "debug-user")
                .putString(KEY_EMAIL, userValue + "@debug.local")
                .putBoolean(KEY_DEBUG_SESSION, true)
                .apply();
    }

    public void clearSession() {
        preferences.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_EXPIRES_AT)
                .remove(KEY_USER_ID)
                .remove(KEY_EMAIL)
                .remove(KEY_DEBUG_SESSION)
                .apply();
    }

    public boolean isLoggedIn() {
        String token = preferences.getString(KEY_ACCESS_TOKEN, null);
        return token != null && !token.trim().isEmpty();
    }

    public boolean isDebugSession() {
        return preferences.getBoolean(KEY_DEBUG_SESSION, false);
    }

    public String getUserId() {
        return preferences.getString(KEY_USER_ID, "");
    }

    public String getEmail() {
        return preferences.getString(KEY_EMAIL, "");
    }

    public String getAccessToken() {
        return preferences.getString(KEY_ACCESS_TOKEN, "");
    }

    public void setKeepSignedIn(boolean keepSignedIn) {
        preferences.edit().putBoolean(KEY_KEEP_SIGNED_IN, keepSignedIn).apply();
    }

    public boolean shouldKeepSignedIn() {
        return preferences.getBoolean(KEY_KEEP_SIGNED_IN, true);
    }
}
