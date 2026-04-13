package com.example.mediavault.cloud.sync;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.example.mediavault.AppExecutor;
import com.example.mediavault.DailyProgress;
import com.example.mediavault.DatabaseHelper;
import com.example.mediavault.cloud.CloudConfig;
import com.example.mediavault.cloud.auth.SupabaseSessionManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
            syncAllMediaFromLocalNow(appContext);
            syncAllProgressLogsFromLocalNow(appContext);
            syncAllDailyMetricsFromLocalNow(appContext);
            syncAllMediaMetadataFromLocalNow(appContext);
            upsertProfileSignalsNow(appContext);
            upsertUserFeatureVectorsNow(appContext);
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
            JsonArray dailyMetricsRows = fetchCloudDailyMetricsRows(session);
            if (dailyMetricsRows != null) {
                applyCloudDailyMetricsToLocalCache(appContext, dailyMetricsRows);
            }
            JsonArray progressLogRows = fetchCloudProgressLogRows(session);
            if (progressLogRows != null) {
                applyCloudProgressLogToLocalCache(appContext, progressLogRows);
            }
            JsonArray metadataRows = fetchCloudMetadataRows(session);
            if (metadataRows != null) {
                applyCloudMetadataToLocalCache(appContext, metadataRows);
            }
        });
    }

    public static void enqueueUserEvent(Context context, String eventType, double eventValue, String sourceSurface) {
        Context appContext = context.getApplicationContext();
        AppExecutor.getInstance().networkIO().execute(() -> insertUserEvent(appContext, eventType, eventValue, sourceSurface));
    }

    public static void enqueueProgressLog(Context context, int localMediaId, double progressAdded, String logDate) {
        if (localMediaId <= 0 || progressAdded <= 0d) return;
        Context appContext = context.getApplicationContext();
        AppExecutor.getInstance().networkIO().execute(
                () -> insertProgressLog(appContext, localMediaId, progressAdded, logDate)
        );
    }

    public static void enqueueUpsertDailyMetrics(Context context, String date) {
        if (TextUtils.isEmpty(date)) return;
        Context appContext = context.getApplicationContext();
        AppExecutor.getInstance().networkIO().execute(
                () -> upsertDailyMetricsNow(appContext, date)
        );
    }

    public static void enqueueUpsertMediaMetadata(Context context, int localMediaId) {
        if (localMediaId <= 0) return;
        Context appContext = context.getApplicationContext();
        AppExecutor.getInstance().networkIO().execute(
                () -> upsertMediaMetadataNow(appContext, localMediaId)
        );
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

    private static void syncAllMediaFromLocalNow(Context context) {
        DatabaseHelper helper = DatabaseHelper.getInstance(context);
        try (Cursor cursor = helper.getAllMediaIncludingTrash()) {
            if (cursor == null || !cursor.moveToFirst()) return;
            do {
                int localId = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
                upsertMediaNow(context, localId);
            } while (cursor.moveToNext());
        }
    }

    private static void syncAllDailyMetricsFromLocalNow(Context context) {
        DatabaseHelper helper = DatabaseHelper.getInstance(context);
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(
                DatabaseHelper.TABLE_DAILY_METRICS,
                new String[]{DatabaseHelper.COL_DAILY_DATE},
                null,
                null,
                null,
                null,
                DatabaseHelper.COL_DAILY_DATE + " DESC"
        )) {
            if (cursor == null || !cursor.moveToFirst()) return;
            do {
                String date = strValue(cursor, DatabaseHelper.COL_DAILY_DATE);
                if (!TextUtils.isEmpty(date)) {
                    upsertDailyMetricsNow(context, date);
                }
            } while (cursor.moveToNext());
        }
    }

    private static void syncAllProgressLogsFromLocalNow(Context context) {
        Session session = session(context);
        if (session == null) return;
        if (cloudHasProgressHistory(session)) return;

        DatabaseHelper helper = DatabaseHelper.getInstance(context);
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(
                DatabaseHelper.TABLE_PROGRESS_LOG,
                new String[]{
                        DatabaseHelper.COL_LOG_MEDIA_ID,
                        DatabaseHelper.COL_PROGRESS_ADDED,
                        DatabaseHelper.COL_LOG_DATE
                },
                null,
                null,
                null,
                null,
                DatabaseHelper.COL_LOG_DATE + " DESC"
        )) {
            if (cursor == null || !cursor.moveToFirst()) return;
            do {
                int localMediaId = intValue(cursor, DatabaseHelper.COL_LOG_MEDIA_ID, 0);
                double progressAdded = dblValue(cursor, DatabaseHelper.COL_PROGRESS_ADDED, 0d);
                String logDate = strValue(cursor, DatabaseHelper.COL_LOG_DATE);
                if (localMediaId > 0 && progressAdded > 0d) {
                    insertProgressLog(session, localMediaId, progressAdded, logDate);
                }
            } while (cursor.moveToNext());
        }
    }

    private static void syncAllMediaMetadataFromLocalNow(Context context) {
        DatabaseHelper helper = DatabaseHelper.getInstance(context);
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(
                DatabaseHelper.TABLE_MEDIA_METADATA,
                new String[]{DatabaseHelper.COL_METADATA_MEDIA_ID},
                null,
                null,
                null,
                null,
                DatabaseHelper.COL_METADATA_UPDATED_AT + " DESC"
        )) {
            if (cursor == null || !cursor.moveToFirst()) return;
            do {
                int localMediaId = intValue(cursor, DatabaseHelper.COL_METADATA_MEDIA_ID, 0);
                if (localMediaId > 0) {
                    upsertMediaMetadataNow(context, localMediaId);
                }
            } while (cursor.moveToNext());
        }
    }

    private static void upsertProfileSignalsNow(Context context) {
        Session session = session(context);
        if (session == null) return;
        JsonObject row = loadLocalProfileSignalsAsJson(context, session.userId);
        if (row == null) return;
        JsonArray payload = new JsonArray();
        payload.add(row);
        api().upsertProfileSignals(
                CloudConfig.getSupabaseAnonKey(),
                "Bearer " + session.accessToken,
                "resolution=merge-duplicates,return=minimal",
                payload
        ).enqueue(new NoopCallback());
    }

    private static void upsertUserFeatureVectorsNow(Context context) {
        Session session = session(context);
        if (session == null) return;
        JsonObject row = loadLocalUserFeatureVectorAsJson(context, session.userId);
        if (row == null) return;
        JsonArray payload = new JsonArray();
        payload.add(row);
        api().upsertUserFeatureVectors(
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

    private static void insertProgressLog(Context context, int localMediaId, double progressAdded, String logDate) {
        Session session = session(context);
        insertProgressLog(session, localMediaId, progressAdded, logDate);
    }

    private static void insertProgressLog(Session session, int localMediaId, double progressAdded, String logDate) {
        if (session == null) return;
        if (progressAdded <= 0d) return;

        String cloudMediaId = fetchCloudMediaId(session, localMediaId);
        if (TextUtils.isEmpty(cloudMediaId)) {
            return;
        }

        JsonObject row = new JsonObject();
        row.addProperty("user_id", session.userId);
        row.addProperty("media_id", cloudMediaId);
        row.addProperty("progress_added", progressAdded);
        if (!TextUtils.isEmpty(logDate)) {
            row.addProperty("log_date", logDate);
        }

        JsonArray payload = new JsonArray();
        payload.add(row);
        api().insertProgressLogs(
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

    private static String fetchCloudMediaId(Session session, int localMediaId) {
        try {
            Response<JsonArray> response = api().getMediaIdByLocalId(
                    CloudConfig.getSupabaseAnonKey(),
                    "Bearer " + session.accessToken,
                    "id",
                    "eq." + session.userId,
                    "eq." + localMediaId,
                    "1"
            ).execute();
            if (!response.isSuccessful() || response.body() == null || response.body().size() == 0) {
                return null;
            }
            JsonObject first = response.body().get(0).getAsJsonObject();
            return strValue(first, "id");
        } catch (IOException ignored) {
            return null;
        }
    }

    private static JsonArray fetchCloudDailyMetricsRows(Session session) {
        try {
            Response<JsonArray> response = api().getDailyMetricsByUser(
                    CloudConfig.getSupabaseAnonKey(),
                    "Bearer " + session.accessToken,
                    "date,chapters_read,episodes_watched,goal_met",
                    "eq." + session.userId,
                    "date.desc"
            ).execute();
            if (!response.isSuccessful()) {
                return null;
            }
            return response.body() == null ? new JsonArray() : response.body();
        } catch (IOException ignored) {
            return null;
        }
    }

    private static JsonArray fetchCloudProgressLogRows(Session session) {
        final int pageSize = 1000;
        int offset = 0;
        JsonArray merged = new JsonArray();
        try {
            while (true) {
                Response<JsonArray> response = api().getProgressLogByUser(
                        CloudConfig.getSupabaseAnonKey(),
                        "Bearer " + session.accessToken,
                        "progress_added,log_date,media_library!inner(local_media_id)",
                        "eq." + session.userId,
                        "log_date.desc",
                        String.valueOf(pageSize),
                        String.valueOf(offset)
                ).execute();
                if (!response.isSuccessful()) {
                    return null;
                }
                JsonArray page = response.body() == null ? new JsonArray() : response.body();
                if (page.size() == 0) {
                    break;
                }
                for (int i = 0; i < page.size(); i++) {
                    merged.add(page.get(i));
                }
                if (page.size() < pageSize) {
                    break;
                }
                offset += page.size();
            }
            return merged;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static boolean cloudHasProgressHistory(Session session) {
        JsonArray rows = fetchCloudProgressLogRows(session);
        return rows != null && rows.size() > 0;
    }

    private static JsonArray fetchCloudMetadataRows(Session session) {
        try {
            Response<JsonArray> response = api().getMediaMetadataByUser(
                    CloudConfig.getSupabaseAnonKey(),
                    "Bearer " + session.accessToken,
                    "canonical_title,normalized_title,alt_titles_json,provider_id,provider_slug,canonical_url,metadata_source,metadata_media_type,metadata_sub_type,metadata_language,metadata_region,metadata_status,metadata_release_year,metadata_total_count,metadata_unit,genres_json,tags_json,external_ids_json,metadata_rating,metadata_popularity,provider_features_json,metadata_confidence,metadata_priority,metadata_updated_at,media_library!inner(local_media_id)",
                    "eq." + session.userId,
                    "metadata_updated_at.desc"
            ).execute();
            if (!response.isSuccessful()) {
                return null;
            }
            return response.body() == null ? new JsonArray() : response.body();
        } catch (IOException ignored) {
            return null;
        }
    }

    private static void upsertDailyMetricsNow(Context context, String date) {
        Session session = session(context);
        if (session == null) return;
        if (TextUtils.isEmpty(date)) return;

        JsonObject row = loadLocalDailyMetricsAsJson(context, session.userId, date);
        if (row == null) return;

        JsonArray payload = new JsonArray();
        payload.add(row);
        api().upsertDailyMetrics(
                CloudConfig.getSupabaseAnonKey(),
                "Bearer " + session.accessToken,
                "resolution=merge-duplicates,return=minimal",
                payload
        ).enqueue(new NoopCallback());
    }

    private static JsonObject loadLocalDailyMetricsAsJson(Context context, String userId, String date) {
        DatabaseHelper helper = DatabaseHelper.getInstance(context);
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(
                DatabaseHelper.TABLE_DAILY_METRICS,
                new String[]{
                        DatabaseHelper.COL_DAILY_DATE,
                        DatabaseHelper.COL_DAILY_CHAPTERS,
                        DatabaseHelper.COL_DAILY_EPISODES,
                        DatabaseHelper.COL_DAILY_GOAL_MET
                },
                DatabaseHelper.COL_DAILY_DATE + "=?",
                new String[]{date},
                null,
                null,
                null,
                "1"
        )) {
            if (cursor == null || !cursor.moveToFirst()) return null;

            JsonObject row = new JsonObject();
            row.addProperty("user_id", userId);
            row.addProperty("date", strValue(cursor, DatabaseHelper.COL_DAILY_DATE));
            row.addProperty("chapters_read", intValue(cursor, DatabaseHelper.COL_DAILY_CHAPTERS, 0));
            row.addProperty("episodes_watched", intValue(cursor, DatabaseHelper.COL_DAILY_EPISODES, 0));
            DailyProgress dailyProgress = helper.getDailyProgress(date);
            int minutesWatched = Math.max(0, Math.round(dailyProgress.minutesWatched));
            row.addProperty("minutes_watched", minutesWatched);
            row.addProperty("goal_met", intValue(cursor, DatabaseHelper.COL_DAILY_GOAL_MET, 0) == 1);
            return row;
        }
    }

    private static JsonObject loadLocalProfileSignalsAsJson(Context context, String userId) {
        DatabaseHelper helper = DatabaseHelper.getInstance(context);
        SQLiteDatabase db = helper.getReadableDatabase();

        int totalMedia = scalarInt(
                db,
                "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_MEDIA + " WHERE " + DatabaseHelper.COL_STATUS + " != ?",
                new String[]{"Recently Deleted"},
                0
        );
        if (totalMedia <= 0) {
            return null;
        }

        List<String> preferredTypes = topColumnValues(db, DatabaseHelper.COL_MEDIA_TYPE, 6);
        List<String> preferredUnits = topColumnValues(db, DatabaseHelper.COL_UNIT, 4);
        GenreStats genreStats = topGenres(db, 10);

        double avgSessionMinutes = scalarDouble(
                db,
                "SELECT AVG(p." + DatabaseHelper.COL_PROGRESS_ADDED + ") " +
                        "FROM " + DatabaseHelper.TABLE_PROGRESS_LOG + " p " +
                        "JOIN " + DatabaseHelper.TABLE_MEDIA + " m ON m." + DatabaseHelper.COL_ID + " = p." + DatabaseHelper.COL_LOG_MEDIA_ID + " " +
                        "WHERE m." + DatabaseHelper.COL_UNIT + " = ?",
                new String[]{"Minutes"},
                0d
        );
        double completionRate = scalarDouble(
                db,
                "SELECT AVG(CASE WHEN " + DatabaseHelper.COL_STATUS + " = 'Completed' THEN 1.0 ELSE 0.0 END) " +
                        "FROM " + DatabaseHelper.TABLE_MEDIA + " WHERE " + DatabaseHelper.COL_STATUS + " != ?",
                new String[]{"Recently Deleted"},
                0d
        );
        double bingeScore = scalarDouble(
                db,
                "SELECT AVG(CASE WHEN " + DatabaseHelper.COL_TOTAL_COUNT + " > 0 AND (" +
                        DatabaseHelper.COL_CURRENT_PROGRESS + " * 1.0 / " + DatabaseHelper.COL_TOTAL_COUNT + ") >= 0.8 THEN 1.0 ELSE 0.0 END) " +
                        "FROM " + DatabaseHelper.TABLE_MEDIA + " WHERE " + DatabaseHelper.COL_STATUS + " != ?",
                new String[]{"Recently Deleted"},
                0d
        );
        double recencyBias = scalarDouble(
                db,
                "SELECT AVG(CASE WHEN julianday('now') - julianday(" + DatabaseHelper.COL_LAST_UPDATED + ") <= 14 THEN 1.0 ELSE 0.0 END) " +
                        "FROM " + DatabaseHelper.TABLE_MEDIA + " WHERE " + DatabaseHelper.COL_STATUS + " != ?",
                new String[]{"Recently Deleted"},
                0d
        );

        double diversityBias = genreStats.totalAssignments > 0
                ? (double) genreStats.uniqueCount / (double) genreStats.totalAssignments
                : 0d;

        JsonObject row = new JsonObject();
        row.addProperty("id", userId);
        row.add("preferred_types", toJsonArray(preferredTypes));
        row.add("preferred_genres", toJsonArray(genreStats.topGenres));
        row.add("preferred_units", toJsonArray(preferredUnits));
        row.addProperty("avg_session_minutes", round3(avgSessionMinutes));
        row.addProperty("binge_score", round3(bingeScore));
        row.addProperty("completion_rate", round3(completionRate));
        row.addProperty("recency_bias", round3(recencyBias));
        row.addProperty("diversity_bias", round3(diversityBias));
        return row;
    }

    private static JsonObject loadLocalUserFeatureVectorAsJson(Context context, String userId) {
        DatabaseHelper helper = DatabaseHelper.getInstance(context);
        SQLiteDatabase db = helper.getReadableDatabase();

        int totalMedia = scalarInt(
                db,
                "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_MEDIA + " WHERE " + DatabaseHelper.COL_STATUS + " != ?",
                new String[]{"Recently Deleted"},
                0
        );
        if (totalMedia <= 0) {
            return null;
        }

        List<String> topTypes = topColumnValues(db, DatabaseHelper.COL_MEDIA_TYPE, 8);
        GenreStats genreStats = topGenres(db, 12);
        JsonArray activeHours = topActiveHours(db, 8);
        JsonObject completionDistribution = completionDistribution(db);

        double freshnessPreference = scalarDouble(
                db,
                "SELECT AVG(CASE WHEN julianday('now') - julianday(" + DatabaseHelper.COL_LAST_UPDATED + ") <= 7 THEN 1.0 ELSE 0.0 END) " +
                        "FROM " + DatabaseHelper.TABLE_MEDIA + " WHERE " + DatabaseHelper.COL_STATUS + " != ?",
                new String[]{"Recently Deleted"},
                0d
        );
        double noveltyPreference = genreStats.totalAssignments > 0
                ? (double) genreStats.uniqueCount / (double) genreStats.totalAssignments
                : 0d;
        double qualityPreference = scalarDouble(
                db,
                "SELECT AVG(" + DatabaseHelper.COL_RATING + " / 5.0) FROM " + DatabaseHelper.TABLE_MEDIA + " " +
                        "WHERE " + DatabaseHelper.COL_STATUS + " != ? AND " + DatabaseHelper.COL_RATING + " > 0",
                new String[]{"Recently Deleted"},
                0d
        );

        JsonObject row = new JsonObject();
        row.addProperty("user_id", userId);
        row.add("top_genres", toJsonArray(genreStats.topGenres));
        row.add("top_types", toJsonArray(topTypes));
        row.add("active_hours", activeHours);
        row.add("completion_distribution", completionDistribution);
        row.addProperty("freshness_preference", round3(freshnessPreference));
        row.addProperty("novelty_preference", round3(noveltyPreference));
        row.addProperty("quality_preference", round3(qualityPreference));
        return row;
    }

    private static void upsertMediaMetadataNow(Context context, int localMediaId) {
        Session session = session(context);
        if (session == null) return;

        String cloudMediaId = fetchCloudMediaId(session, localMediaId);
        if (TextUtils.isEmpty(cloudMediaId)) return;

        JsonObject row = loadLocalMetadataAsJson(context, localMediaId, cloudMediaId);
        if (row == null) return;

        JsonArray payload = new JsonArray();
        payload.add(row);
        api().upsertMediaMetadata(
                CloudConfig.getSupabaseAnonKey(),
                "Bearer " + session.accessToken,
                "resolution=merge-duplicates,return=minimal",
                payload
        ).enqueue(new NoopCallback());
    }

    private static JsonObject loadLocalMetadataAsJson(Context context, int localMediaId, String cloudMediaId) {
        DatabaseHelper helper = DatabaseHelper.getInstance(context);
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(
                DatabaseHelper.TABLE_MEDIA_METADATA,
                null,
                DatabaseHelper.COL_METADATA_MEDIA_ID + "=?",
                new String[]{String.valueOf(localMediaId)},
                null,
                null,
                null,
                "1"
        )) {
            if (cursor == null || !cursor.moveToFirst()) return null;

            JsonObject row = new JsonObject();
            row.addProperty("media_id", cloudMediaId);
            putString(row, "canonical_title", cursor, DatabaseHelper.COL_METADATA_CANONICAL_TITLE);
            putString(row, "normalized_title", cursor, DatabaseHelper.COL_METADATA_NORMALIZED_TITLE);
            putJson(row, "alt_titles_json", cursor, DatabaseHelper.COL_METADATA_ALT_TITLES_JSON);
            putString(row, "provider_id", cursor, DatabaseHelper.COL_METADATA_PROVIDER_ID);
            putString(row, "provider_slug", cursor, DatabaseHelper.COL_METADATA_PROVIDER_SLUG);
            putString(row, "canonical_url", cursor, DatabaseHelper.COL_METADATA_CANONICAL_URL);
            putString(row, "metadata_source", cursor, DatabaseHelper.COL_METADATA_SOURCE);
            putString(row, "metadata_media_type", cursor, DatabaseHelper.COL_METADATA_MEDIA_TYPE);
            putString(row, "metadata_sub_type", cursor, DatabaseHelper.COL_METADATA_SUB_TYPE);
            putString(row, "metadata_language", cursor, DatabaseHelper.COL_METADATA_LANGUAGE);
            putString(row, "metadata_region", cursor, DatabaseHelper.COL_METADATA_REGION);
            putString(row, "metadata_status", cursor, DatabaseHelper.COL_METADATA_STATUS);
            putInt(row, "metadata_release_year", cursor, DatabaseHelper.COL_METADATA_RELEASE_YEAR);
            putInt(row, "metadata_total_count", cursor, DatabaseHelper.COL_METADATA_TOTAL_COUNT);
            putString(row, "metadata_unit", cursor, DatabaseHelper.COL_METADATA_UNIT);
            putJson(row, "genres_json", cursor, DatabaseHelper.COL_METADATA_GENRES_JSON);
            putJson(row, "tags_json", cursor, DatabaseHelper.COL_METADATA_TAGS_JSON);
            putJson(row, "external_ids_json", cursor, DatabaseHelper.COL_METADATA_EXTERNAL_IDS_JSON);
            putNumber(row, "metadata_rating", cursor, DatabaseHelper.COL_METADATA_RATING);
            putNumber(row, "metadata_popularity", cursor, DatabaseHelper.COL_METADATA_POPULARITY);
            putJson(row, "provider_features_json", cursor, DatabaseHelper.COL_METADATA_PROVIDER_FEATURES_JSON);
            putNumber(row, "metadata_confidence", cursor, DatabaseHelper.COL_METADATA_CONFIDENCE);
            putInt(row, "metadata_priority", cursor, DatabaseHelper.COL_METADATA_PRIORITY);
            putString(row, "metadata_updated_at", cursor, DatabaseHelper.COL_METADATA_UPDATED_AT);
            return row;
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

    private static void applyCloudDailyMetricsToLocalCache(Context context, JsonArray rows) {
        DatabaseHelper helper = DatabaseHelper.getInstance(context);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(DatabaseHelper.TABLE_DAILY_METRICS, null, null);
            for (int i = 0; i < rows.size(); i++) {
                if (!rows.get(i).isJsonObject()) continue;
                JsonObject obj = rows.get(i).getAsJsonObject();
                String date = strValue(obj, "date");
                if (TextUtils.isEmpty(date)) continue;

                ContentValues v = new ContentValues();
                v.put(DatabaseHelper.COL_DAILY_DATE, date);
                Integer chapters = intValue(obj, "chapters_read");
                Integer episodes = intValue(obj, "episodes_watched");
                v.put(DatabaseHelper.COL_DAILY_CHAPTERS, chapters != null ? chapters : 0);
                v.put(DatabaseHelper.COL_DAILY_EPISODES, episodes != null ? episodes : 0);
                v.put(DatabaseHelper.COL_DAILY_GOAL_MET, boolValue(obj, "goal_met") ? 1 : 0);
                db.insertWithOnConflict(DatabaseHelper.TABLE_DAILY_METRICS, null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    private static void applyCloudProgressLogToLocalCache(Context context, JsonArray rows) {
        DatabaseHelper helper = DatabaseHelper.getInstance(context);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(DatabaseHelper.TABLE_PROGRESS_LOG, null, null);
            for (int i = 0; i < rows.size(); i++) {
                if (!rows.get(i).isJsonObject()) continue;
                JsonObject obj = rows.get(i).getAsJsonObject();
                int localMediaId = extractLocalMediaId(obj);
                if (localMediaId <= 0) continue;

                double progressAdded = dblValue(obj, "progress_added", 0d);
                if (progressAdded <= 0d) continue;
                String logDate = strValue(obj, "log_date");
                if (TextUtils.isEmpty(logDate)) continue;

                ContentValues v = new ContentValues();
                v.put(DatabaseHelper.COL_LOG_MEDIA_ID, localMediaId);
                v.put(DatabaseHelper.COL_PROGRESS_ADDED, progressAdded);
                v.put(DatabaseHelper.COL_LOG_DATE, logDate);
                db.insert(DatabaseHelper.TABLE_PROGRESS_LOG, null, v);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    private static void applyCloudMetadataToLocalCache(Context context, JsonArray rows) {
        DatabaseHelper helper = DatabaseHelper.getInstance(context);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(DatabaseHelper.TABLE_MEDIA_METADATA, null, null);
            for (int i = 0; i < rows.size(); i++) {
                if (!rows.get(i).isJsonObject()) continue;
                JsonObject obj = rows.get(i).getAsJsonObject();
                int localMediaId = extractLocalMediaId(obj);
                if (localMediaId <= 0) continue;

                ContentValues v = new ContentValues();
                v.put(DatabaseHelper.COL_METADATA_MEDIA_ID, localMediaId);
                v.put(DatabaseHelper.COL_METADATA_CANONICAL_TITLE, strValue(obj, "canonical_title"));
                v.put(DatabaseHelper.COL_METADATA_NORMALIZED_TITLE, strValue(obj, "normalized_title"));
                v.put(DatabaseHelper.COL_METADATA_ALT_TITLES_JSON, jsonStringValue(obj, "alt_titles_json"));
                v.put(DatabaseHelper.COL_METADATA_PROVIDER_ID, strValue(obj, "provider_id"));
                v.put(DatabaseHelper.COL_METADATA_PROVIDER_SLUG, strValue(obj, "provider_slug"));
                v.put(DatabaseHelper.COL_METADATA_CANONICAL_URL, strValue(obj, "canonical_url"));
                v.put(DatabaseHelper.COL_METADATA_SOURCE, strValue(obj, "metadata_source"));
                v.put(DatabaseHelper.COL_METADATA_MEDIA_TYPE, strValue(obj, "metadata_media_type"));
                v.put(DatabaseHelper.COL_METADATA_SUB_TYPE, strValue(obj, "metadata_sub_type"));
                v.put(DatabaseHelper.COL_METADATA_LANGUAGE, strValue(obj, "metadata_language"));
                v.put(DatabaseHelper.COL_METADATA_REGION, strValue(obj, "metadata_region"));
                v.put(DatabaseHelper.COL_METADATA_STATUS, strValue(obj, "metadata_status"));
                Integer releaseYear = intValue(obj, "metadata_release_year");
                if (releaseYear != null) v.put(DatabaseHelper.COL_METADATA_RELEASE_YEAR, releaseYear);
                Integer totalCount = intValue(obj, "metadata_total_count");
                if (totalCount != null) v.put(DatabaseHelper.COL_METADATA_TOTAL_COUNT, totalCount);
                v.put(DatabaseHelper.COL_METADATA_UNIT, strValue(obj, "metadata_unit"));
                v.put(DatabaseHelper.COL_METADATA_GENRES_JSON, jsonStringValue(obj, "genres_json"));
                v.put(DatabaseHelper.COL_METADATA_TAGS_JSON, jsonStringValue(obj, "tags_json"));
                v.put(DatabaseHelper.COL_METADATA_EXTERNAL_IDS_JSON, jsonStringValue(obj, "external_ids_json"));
                if (obj.has("metadata_rating") && !obj.get("metadata_rating").isJsonNull()) {
                    v.put(DatabaseHelper.COL_METADATA_RATING, dblValue(obj, "metadata_rating", 0d));
                }
                if (obj.has("metadata_popularity") && !obj.get("metadata_popularity").isJsonNull()) {
                    v.put(DatabaseHelper.COL_METADATA_POPULARITY, dblValue(obj, "metadata_popularity", 0d));
                }
                v.put(DatabaseHelper.COL_METADATA_PROVIDER_FEATURES_JSON, jsonStringValue(obj, "provider_features_json"));
                if (obj.has("metadata_confidence") && !obj.get("metadata_confidence").isJsonNull()) {
                    v.put(DatabaseHelper.COL_METADATA_CONFIDENCE, dblValue(obj, "metadata_confidence", 0d));
                }
                Integer priority = intValue(obj, "metadata_priority");
                if (priority != null) v.put(DatabaseHelper.COL_METADATA_PRIORITY, priority);
                v.put(DatabaseHelper.COL_METADATA_UPDATED_AT, strValue(obj, "metadata_updated_at"));

                db.insertWithOnConflict(DatabaseHelper.TABLE_MEDIA_METADATA, null, v, SQLiteDatabase.CONFLICT_REPLACE);
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

    private static void putInt(JsonObject row, String key, Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0 || cursor.isNull(index)) return;
        row.addProperty(key, cursor.getInt(index));
    }

    private static void putJson(JsonObject row, String key, Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0 || cursor.isNull(index)) return;
        String raw = cursor.getString(index);
        if (TextUtils.isEmpty(raw)) return;
        try {
            JsonElement parsed = JsonParser.parseString(raw);
            row.add(key, parsed);
        } catch (JsonSyntaxException ignored) {
            // Keep local data authoritative; skip malformed JSON payload fragments only.
        }
    }

    private static String strValue(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0 || cursor.isNull(index)) return null;
        return cursor.getString(index);
    }

    private static double dblValue(Cursor cursor, String column, double fallback) {
        int index = cursor.getColumnIndex(column);
        if (index < 0 || cursor.isNull(index)) return fallback;
        return cursor.getDouble(index);
    }

    private static int intValue(Cursor cursor, String column, int fallback) {
        int index = cursor.getColumnIndex(column);
        if (index < 0 || cursor.isNull(index)) return fallback;
        return cursor.getInt(index);
    }

    private static String strValue(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return null;
        String value = obj.get(key).getAsString();
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private static String jsonStringValue(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return null;
        return obj.get(key).toString();
    }

    private static int extractLocalMediaId(JsonObject metadataRow) {
        if (!metadataRow.has("media_library") || metadataRow.get("media_library").isJsonNull()) {
            return 0;
        }
        JsonElement mediaLibraryElement = metadataRow.get("media_library");
        if (mediaLibraryElement.isJsonObject()) {
            Integer localId = intValue(mediaLibraryElement.getAsJsonObject(), "local_media_id");
            return localId != null ? localId : 0;
        }
        if (mediaLibraryElement.isJsonArray() && mediaLibraryElement.getAsJsonArray().size() > 0) {
            JsonElement first = mediaLibraryElement.getAsJsonArray().get(0);
            if (first.isJsonObject()) {
                Integer localId = intValue(first.getAsJsonObject(), "local_media_id");
                return localId != null ? localId : 0;
            }
        }
        return 0;
    }

    private static List<String> topColumnValues(SQLiteDatabase db, String column, int limit) {
        List<String> values = new ArrayList<>();
        String query = "SELECT " + column + ", COUNT(*) c FROM " + DatabaseHelper.TABLE_MEDIA + " " +
                "WHERE " + DatabaseHelper.COL_STATUS + " != ? AND " + column + " IS NOT NULL AND TRIM(" + column + ") != '' " +
                "GROUP BY " + column + " ORDER BY c DESC, " + column + " ASC LIMIT " + limit;
        try (Cursor cursor = db.rawQuery(query, new String[]{"Recently Deleted"})) {
            if (cursor == null || !cursor.moveToFirst()) return values;
            do {
                String value = cursor.getString(0);
                if (!TextUtils.isEmpty(value)) {
                    values.add(value.trim());
                }
            } while (cursor.moveToNext());
        }
        return values;
    }

    private static GenreStats topGenres(SQLiteDatabase db, int limit) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        int totalAssignments = 0;
        String query = "SELECT " + DatabaseHelper.COL_GENRE + " FROM " + DatabaseHelper.TABLE_MEDIA + " " +
                "WHERE " + DatabaseHelper.COL_STATUS + " != ? AND " + DatabaseHelper.COL_GENRE + " IS NOT NULL AND TRIM(" + DatabaseHelper.COL_GENRE + ") != ''";
        try (Cursor cursor = db.rawQuery(query, new String[]{"Recently Deleted"})) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String raw = cursor.getString(0);
                    if (TextUtils.isEmpty(raw)) continue;
                    String[] split = raw.split("[,;/|]");
                    for (String token : split) {
                        String cleaned = token == null ? "" : token.trim();
                        if (cleaned.isEmpty()) continue;
                        String key = cleaned.toLowerCase(Locale.US);
                        Integer current = counts.get(key);
                        counts.put(key, (current == null ? 0 : current) + 1);
                        if (!labels.containsKey(key)) {
                            labels.put(key, cleaned);
                        }
                        totalAssignments++;
                    }
                } while (cursor.moveToNext());
            }
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Comparator
                .comparing(Map.Entry<String, Integer>::getValue, Comparator.reverseOrder())
                .thenComparing(Map.Entry::getKey));

        List<String> topGenres = new ArrayList<>();
        for (int i = 0; i < entries.size() && i < limit; i++) {
            String key = entries.get(i).getKey();
            String label = labels.get(key);
            if (!TextUtils.isEmpty(label)) {
                topGenres.add(label);
            }
        }
        return new GenreStats(topGenres, counts.size(), totalAssignments);
    }

    private static JsonArray topActiveHours(SQLiteDatabase db, int limit) {
        JsonArray hours = new JsonArray();
        String query = "SELECT CAST(strftime('%H', " + DatabaseHelper.COL_LAST_UPDATED + ") AS INTEGER) AS h, COUNT(*) c " +
                "FROM " + DatabaseHelper.TABLE_MEDIA + " WHERE " + DatabaseHelper.COL_STATUS + " != ? " +
                "AND " + DatabaseHelper.COL_LAST_UPDATED + " IS NOT NULL " +
                "GROUP BY h HAVING h IS NOT NULL ORDER BY c DESC, h ASC LIMIT " + limit;
        try (Cursor cursor = db.rawQuery(query, new String[]{"Recently Deleted"})) {
            if (cursor == null || !cursor.moveToFirst()) return hours;
            do {
                if (!cursor.isNull(0)) {
                    hours.add(cursor.getInt(0));
                }
            } while (cursor.moveToNext());
        }
        return hours;
    }

    private static JsonObject completionDistribution(SQLiteDatabase db) {
        JsonObject dist = new JsonObject();
        int total = 0;
        String query = "SELECT " + DatabaseHelper.COL_STATUS + ", COUNT(*) c FROM " + DatabaseHelper.TABLE_MEDIA + " " +
                "WHERE " + DatabaseHelper.COL_STATUS + " != ? GROUP BY " + DatabaseHelper.COL_STATUS;
        try (Cursor cursor = db.rawQuery(query, new String[]{"Recently Deleted"})) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    total += cursor.getInt(1);
                } while (cursor.moveToNext());
            }
            if (cursor != null) {
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    String status = cursor.getString(0);
                    int count = cursor.getInt(1);
                    String key = normalizeDistributionKey(status);
                    dist.addProperty(key, total > 0 ? round3((double) count / (double) total) : 0d);
                    cursor.moveToNext();
                }
            }
        }
        return dist;
    }

    private static String normalizeDistributionKey(String status) {
        if (TextUtils.isEmpty(status)) return "unknown";
        return status.trim().toLowerCase(Locale.US).replace(' ', '_');
    }

    private static int scalarInt(SQLiteDatabase db, String query, String[] args, int fallback) {
        try (Cursor cursor = db.rawQuery(query, args)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getInt(0);
            }
        }
        return fallback;
    }

    private static double scalarDouble(SQLiteDatabase db, String query, String[] args, double fallback) {
        try (Cursor cursor = db.rawQuery(query, args)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getDouble(0);
            }
        }
        return fallback;
    }

    private static JsonArray toJsonArray(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) {
                array.add(value);
            }
        }
        return array;
    }

    private static double round3(double value) {
        return Math.round(value * 1000d) / 1000d;
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

    private static class GenreStats {
        final List<String> topGenres;
        final int uniqueCount;
        final int totalAssignments;

        GenreStats(List<String> topGenres, int uniqueCount, int totalAssignments) {
            this.topGenres = topGenres;
            this.uniqueCount = uniqueCount;
            this.totalAssignments = totalAssignments;
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
