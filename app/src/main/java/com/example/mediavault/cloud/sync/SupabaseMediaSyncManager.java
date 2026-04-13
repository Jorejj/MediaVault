package com.example.mediavault.cloud.sync;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.example.mediavault.AppExecutor;
import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.cloud.CloudConfig;
import com.example.mediavault.cloud.auth.SupabaseSessionManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class SupabaseMediaSyncManager {
    private SupabaseMediaSyncManager() {}

    public static void enqueueUpsertMedia(Context context, int localMediaId) {
        if (localMediaId <= 0) return;
        Context appContext = context.getApplicationContext();
        AppExecutor.getInstance().networkIO().execute(() -> upsertMediaNow(appContext, localMediaId));
    }

    public static void enqueueDeleteMedia(Context context, int localMediaId) {
        if (localMediaId <= 0) return;
        Context appContext = context.getApplicationContext();
        AppExecutor.getInstance().networkIO().execute(() -> deleteMediaNow(appContext, localMediaId));
    }

    public static void syncAllFromLocalAsync(Context context) {
        Context appContext = context.getApplicationContext();
        AppExecutor.getInstance().networkIO().execute(() -> {
            if (!canSync(appContext)) return;
            DatabaseHelper helper = DatabaseHelper.getInstance(appContext);
            try (Cursor cursor = helper.getAllMediaIncludingTrash()) {
                if (cursor == null || !cursor.moveToFirst()) return;
                do {
                    int localId = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
                    upsertMediaNow(appContext, localId);
                } while (cursor.moveToNext());
            }
        });
    }

    public static void bootstrapCloudPrimaryAsync(Context context) {
        Context appContext = context.getApplicationContext();
        AppExecutor.getInstance().networkIO().execute(() -> {
            Session session = session(appContext);
            if (session == null) return;
            JsonArray cloudRows = fetchCloudRows(session);
            if (cloudRows == null) {
                return;
            }
            if (cloudRows.size() == 0) {
                syncAllFromLocalAsync(appContext);
                return;
            }
            applyCloudRowsToLocalCache(appContext, cloudRows);
        });
    }

    public static void enqueueUserEvent(Context context, String eventType, double eventValue, String sourceSurface) {
        Context appContext = context.getApplicationContext();
        AppExecutor.getInstance().networkIO().execute(() -> insertUserEvent(appContext, eventType, eventValue, sourceSurface));
    }

    private static void upsertMediaNow(Context context, int localMediaId) {
        Session session = session(context);
        if (session == null) return;

        JsonObject row = loadLocalMediaAsJson(context, localMediaId, session.userId);
        if (row == null) return;

        JsonArray payload = new JsonArray();
        payload.add(row);

        api().upsertMedia(
                CloudConfig.getSupabaseAnonKey(),
                "Bearer " + session.accessToken,
                "resolution=merge-duplicates,return=minimal",
                payload
        ).enqueue(new NoopCallback());
    }

    private static void deleteMediaNow(Context context, int localMediaId) {
        Session session = session(context);
        if (session == null) return;
        api().deleteMediaByLocalId(
                CloudConfig.getSupabaseAnonKey(),
                "Bearer " + session.accessToken,
                "eq." + session.userId,
                "eq." + localMediaId
        ).enqueue(new NoopCallback());
    }

    private static JsonObject loadLocalMediaAsJson(Context context, int localMediaId, String userId) {
        DatabaseHelper helper = DatabaseHelper.getInstance(context);
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(
                "SELECT * FROM " + DatabaseHelper.TABLE_MEDIA + " WHERE " + DatabaseHelper.COL_ID + "=?",
                new String[]{String.valueOf(localMediaId)}
        )) {
            if (cursor == null || !cursor.moveToFirst()) return null;

            JsonObject row = new JsonObject();
            row.addProperty("user_id", userId);
            row.addProperty("local_media_id", localMediaId);
            putString(row, "api_id", cursor, DatabaseHelper.COL_API_ID);
            putString(row, "title", cursor, DatabaseHelper.COL_TITLE);
            putString(row, "description", cursor, DatabaseHelper.COL_DESCRIPTION);
            putString(row, "creator", cursor, DatabaseHelper.COL_CREATOR);
            putString(row, "media_type", cursor, DatabaseHelper.COL_MEDIA_TYPE);
            putString(row, "genre", cursor, DatabaseHelper.COL_GENRE);
            putString(row, "image_path", cursor, DatabaseHelper.COL_IMAGE_PATH);
            putNumber(row, "current_progress", cursor, DatabaseHelper.COL_CURRENT_PROGRESS);
            putNumber(row, "previous_progress", cursor, DatabaseHelper.COL_PREVIOUS_PROGRESS);
            row.addProperty("total_count", cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_COUNT)));
            putString(row, "capacity_unit", cursor, DatabaseHelper.COL_UNIT);
            putString(row, "runtime", cursor, DatabaseHelper.COL_RUNTIME);
            putString(row, "status", cursor, DatabaseHelper.COL_STATUS);
            putNumber(row, "user_rating", cursor, DatabaseHelper.COL_RATING);
            putString(row, "personal_review", cursor, DatabaseHelper.COL_REVIEW);
            putString(row, "memory_journal", cursor, DatabaseHelper.COL_JOURNAL);
            putString(row, "finish_mood", cursor, DatabaseHelper.COL_MOOD);
            putString(row, "priority_level", cursor, DatabaseHelper.COL_PRIORITY);
            putString(row, "date_added", cursor, DatabaseHelper.COL_DATE_ADDED);
            putString(row, "last_updated", cursor, DatabaseHelper.COL_LAST_UPDATED);
            row.addProperty("is_favorite", cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_IS_FAVORITE)) == 1);
            putString(row, "source_url", cursor, DatabaseHelper.COL_SOURCE_URL);
            putString(row, "content_type", cursor, DatabaseHelper.COL_CONTENT_TYPE);
            row.addProperty("current_season", cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CURRENT_SEASON)));
            row.addProperty("current_episode", cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CURRENT_EPISODE)));
            return row;
        }
    }

    private static void insertUserEvent(Context context, String eventType, double eventValue, String sourceSurface) {
        Session session = session(context);
        if (session == null) return;
        if (TextUtils.isEmpty(eventType)) return;

        JsonObject row = new JsonObject();
        row.addProperty("user_id", session.userId);
        row.addProperty("event_type", eventType);
        row.addProperty("event_value", Math.max(0d, eventValue));
        if (!TextUtils.isEmpty(sourceSurface)) {
            row.addProperty("source_surface", sourceSurface);
        }
        row.addProperty("device_platform", "android");

        JsonArray payload = new JsonArray();
        payload.add(row);
        api().insertMediaEvents(
                CloudConfig.getSupabaseAnonKey(),
                "Bearer " + session.accessToken,
                "return=minimal",
                payload
        ).enqueue(new NoopCallback());
    }

    private static JsonArray fetchCloudRows(Session session) {
        try {
            Response<JsonArray> response = api().getMediaByUser(
                    CloudConfig.getSupabaseAnonKey(),
                    "Bearer " + session.accessToken,
                    "*",
                    "eq." + session.userId,
                    "last_updated.desc"
            ).execute();
            if (!response.isSuccessful()) {
                return null;
            }
            return response.body() == null ? new JsonArray() : response.body();
        } catch (IOException ignored) {
            return null;
        }
    }

    private static void applyCloudRowsToLocalCache(Context context, JsonArray rows) {
        DatabaseHelper helper = DatabaseHelper.getInstance(context);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(DatabaseHelper.TABLE_MEDIA, null, null);
            for (int i = 0; i < rows.size(); i++) {
                if (!rows.get(i).isJsonObject()) continue;
                JsonObject obj = rows.get(i).getAsJsonObject();
                ContentValues v = new ContentValues();
                Integer localId = intValue(obj, "local_media_id");
                if (localId != null && localId > 0) {
                    v.put(DatabaseHelper.COL_ID, localId);
                }
                v.put(DatabaseHelper.COL_API_ID, strValue(obj, "api_id"));
                v.put(DatabaseHelper.COL_TITLE, nonEmpty(strValue(obj, "title"), "Untitled"));
                v.put(DatabaseHelper.COL_DESCRIPTION, strValue(obj, "description"));
                v.put(DatabaseHelper.COL_CREATOR, strValue(obj, "creator"));
                v.put(DatabaseHelper.COL_MEDIA_TYPE, nonEmpty(strValue(obj, "media_type"), "Movie"));
                v.put(DatabaseHelper.COL_GENRE, strValue(obj, "genre"));
                v.put(DatabaseHelper.COL_IMAGE_PATH, strValue(obj, "image_path"));
                v.put(DatabaseHelper.COL_CURRENT_PROGRESS, dblValue(obj, "current_progress", 0d));
                v.put(DatabaseHelper.COL_PREVIOUS_PROGRESS, dblValue(obj, "previous_progress", 0d));
                v.put(DatabaseHelper.COL_TOTAL_COUNT, intValue(obj, "total_count") != null ? intValue(obj, "total_count") : 1);
                v.put(DatabaseHelper.COL_UNIT, nonEmpty(strValue(obj, "capacity_unit"), "Episodes"));
                v.put(DatabaseHelper.COL_RUNTIME, strValue(obj, "runtime"));
                v.put(DatabaseHelper.COL_STATUS, nonEmpty(strValue(obj, "status"), "Planning"));
                v.put(DatabaseHelper.COL_RATING, dblValue(obj, "user_rating", 0d));
                v.put(DatabaseHelper.COL_REVIEW, strValue(obj, "personal_review"));
                v.put(DatabaseHelper.COL_JOURNAL, strValue(obj, "memory_journal"));
                v.put(DatabaseHelper.COL_MOOD, strValue(obj, "finish_mood"));
                v.put(DatabaseHelper.COL_PRIORITY, nonEmpty(strValue(obj, "priority_level"), "Medium"));
                v.put(DatabaseHelper.COL_DATE_ADDED, strValue(obj, "date_added"));
                v.put(DatabaseHelper.COL_LAST_UPDATED, strValue(obj, "last_updated"));
                v.put(DatabaseHelper.COL_IS_FAVORITE, boolValue(obj, "is_favorite") ? 1 : 0);
                v.put(DatabaseHelper.COL_SOURCE_URL, strValue(obj, "source_url"));
                v.put(DatabaseHelper.COL_CONTENT_TYPE, strValue(obj, "content_type"));
                v.put(DatabaseHelper.COL_CURRENT_SEASON, intValue(obj, "current_season") != null ? intValue(obj, "current_season") : 1);
                v.put(DatabaseHelper.COL_CURRENT_EPISODE, intValue(obj, "current_episode") != null ? intValue(obj, "current_episode") : 1);
                db.insertWithOnConflict(DatabaseHelper.TABLE_MEDIA, null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    private static void putString(JsonObject row, String key, Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0 || cursor.isNull(index)) return;
        String value = cursor.getString(index);
        if (TextUtils.isEmpty(value)) return;
        row.addProperty(key, value);
    }

    private static void putNumber(JsonObject row, String key, Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0 || cursor.isNull(index)) return;
        row.addProperty(key, cursor.getDouble(index));
    }

    private static String strValue(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return null;
        String value = obj.get(key).getAsString();
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private static Integer intValue(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return null;
        try {
            return obj.get(key).getAsInt();
        } catch (NumberFormatException | UnsupportedOperationException | ClassCastException ignored) {
            return null;
        }
    }

    private static double dblValue(JsonObject obj, String key, double fallback) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return fallback;
        try {
            return obj.get(key).getAsDouble();
        } catch (NumberFormatException | UnsupportedOperationException | ClassCastException ignored) {
            return fallback;
        }
    }

    private static boolean boolValue(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return false;
        try {
            return obj.get(key).getAsBoolean();
        } catch (UnsupportedOperationException | ClassCastException ignored) {
            return false;
        }
    }

    private static String nonEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static boolean canSync(Context context) {
        return session(context) != null;
    }

    private static Session session(Context context) {
        if (!CloudConfig.isSupabaseEnabled()) return null;
        SupabaseSessionManager sessionManager = new SupabaseSessionManager(context);
        String userId = sessionManager.getUserId();
        String accessToken = sessionManager.getAccessToken();
        if (TextUtils.isEmpty(userId) || TextUtils.isEmpty(accessToken)) return null;
        return new Session(userId, accessToken);
    }

    private static SupabaseMediaApi api() {
        return new Retrofit.Builder()
                .baseUrl(normalizedBaseUrl())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SupabaseMediaApi.class);
    }

    private static String normalizedBaseUrl() {
        String raw = CloudConfig.getSupabaseUrl();
        return raw.endsWith("/") ? raw : raw + "/";
    }

    private static class Session {
        final String userId;
        final String accessToken;

        Session(String userId, String accessToken) {
            this.userId = userId;
            this.accessToken = accessToken;
        }
    }

    private static class NoopCallback implements Callback<Void> {
        @Override
        public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
            // Intentionally no-op: cloud sync is best-effort cache synchronization.
        }

        @Override
        public void onFailure(@NonNull Call<Void> call, @NonNull Throwable throwable) {
            // Intentionally no-op: caller remains functional with local cache.
        }
    }
}
