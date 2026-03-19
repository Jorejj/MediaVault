package com.example.mediavault;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DatabaseHelper extends SQLiteOpenHelper {

    // Database Information
    private static final String DATABASE_NAME = "MediaVault.db";
    private static final int DATABASE_VERSION = 12;
    public static final String TABLE_MEDIA = "media_library";
    public static final String TABLE_PROGRESS_LOG = "progress_log";

    // Column Constants for media_library
    public static final String COL_ID = "id";
    public static final String COL_API_ID = "api_id";
    public static final String COL_TITLE = "title";
    public static final String COL_DESCRIPTION = "description";
    public static final String COL_CREATOR = "creator";
    public static final String COL_MEDIA_TYPE = "media_type";
    public static final String COL_GENRE = "genre";
    public static final String COL_IMAGE_PATH = "image_path";
    public static final String COL_CURRENT_PROGRESS = "current_progress";
    public static final String COL_TOTAL_COUNT = "total_count";
    public static final String COL_UNIT = "capacity_unit";
    public static final String COL_RUNTIME = "runtime";
    public static final String COL_STATUS = "status";
    public static final String COL_RATING = "user_rating";
    public static final String COL_REVIEW = "personal_review";
    public static final String COL_JOURNAL = "memory_journal";
    public static final String COL_MOOD = "finish_mood";
    public static final String COL_PRIORITY = "priority_level";
    public static final String COL_DATE_ADDED = "date_added";
    public static final String COL_LAST_UPDATED = "last_updated";
    public static final String COL_IS_FAVORITE = "is_favorite";

    // Column Constants for progress_log
    public static final String COL_LOG_ID = "log_id";
    public static final String COL_LOG_MEDIA_ID = "media_id";
    public static final String COL_PROGRESS_ADDED = "progress_added";
    public static final String COL_LOG_DATE = "log_date";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create media_library table
        String createMediaTable = "CREATE TABLE " + TABLE_MEDIA + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_API_ID + " TEXT, " +
                COL_TITLE + " TEXT NOT NULL COLLATE NOCASE, " +
                COL_DESCRIPTION + " TEXT, " +
                COL_CREATOR + " TEXT, " +
                COL_MEDIA_TYPE + " TEXT NOT NULL, " +
                COL_GENRE + " TEXT, " +
                COL_IMAGE_PATH + " TEXT, " +
                COL_CURRENT_PROGRESS + " INTEGER DEFAULT 0, " +
                COL_TOTAL_COUNT + " INTEGER NOT NULL, " +
                COL_UNIT + " TEXT NOT NULL, " +
                COL_RUNTIME + " TEXT, " +
                COL_STATUS + " TEXT DEFAULT 'Planning', " +
                COL_RATING + " REAL DEFAULT 0.0, " +
                COL_REVIEW + " TEXT, " +
                COL_JOURNAL + " TEXT, " +
                COL_MOOD + " TEXT, " +
                COL_PRIORITY + " TEXT DEFAULT 'Medium', " +
                COL_DATE_ADDED + " DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                COL_LAST_UPDATED + " DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                COL_IS_FAVORITE + " INTEGER DEFAULT 0, " +
                "CONSTRAINT unique_title UNIQUE (" + COL_TITLE + " COLLATE NOCASE), " +
                "CONSTRAINT check_status CHECK (" + COL_STATUS + " IN ('Ongoing', 'Completed', 'Planning', 'Dropped', 'Recently Deleted')), " +
                "CONSTRAINT check_capacity_unit CHECK (" + COL_UNIT + " IN ('Pages', 'Episodes', 'Minutes', 'Chapters')), " +
                "CONSTRAINT check_user_rating CHECK (" + COL_RATING + " >= 0.0 AND " + COL_RATING + " <= 5.0), " +
                "CONSTRAINT check_priority_level CHECK (" + COL_PRIORITY + " IN ('High', 'Medium', 'Low')), " +
                "CONSTRAINT check_total_capacity CHECK (" + COL_TOTAL_COUNT + " > 0), " +
                "CONSTRAINT check_current_progress CHECK (" + COL_CURRENT_PROGRESS + " >= 0 AND " + COL_CURRENT_PROGRESS + " <= " + COL_TOTAL_COUNT + "), " +
                "CONSTRAINT check_image_path CHECK (" + COL_IMAGE_PATH + " IS NULL OR " + COL_IMAGE_PATH + " != ''))";
        
        db.execSQL(createMediaTable);

        // Create progress_log table for metrics
        String createLogTable = "CREATE TABLE " + TABLE_PROGRESS_LOG + " (" +
                COL_LOG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_LOG_MEDIA_ID + " INTEGER NOT NULL, " +
                COL_PROGRESS_ADDED + " INTEGER NOT NULL, " +
                COL_LOG_DATE + " DATE DEFAULT (date('now', 'localtime')), " +
                "FOREIGN KEY (" + COL_LOG_MEDIA_ID + ") REFERENCES " + TABLE_MEDIA + "(" + COL_ID + ") ON DELETE CASCADE)";
        
        db.execSQL(createLogTable);

        createTrigger(db);
    }

    private void createTrigger(SQLiteDatabase db) {
        String createTrigger = "CREATE TRIGGER IF NOT EXISTS update_media_modtime " +
                "AFTER UPDATE ON " + TABLE_MEDIA + " " +
                "FOR EACH ROW BEGIN " +
                "UPDATE " + TABLE_MEDIA + " SET " + COL_LAST_UPDATED + " = CURRENT_TIMESTAMP " +
                "WHERE " + COL_ID + " = OLD." + COL_ID + "; END;";
        db.execSQL(createTrigger);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 8) {
            Set<String> columns = getTableColumns(db, TABLE_MEDIA);
            
            if (!columns.contains(COL_REVIEW)) {
                db.execSQL("ALTER TABLE " + TABLE_MEDIA + " ADD COLUMN " + COL_REVIEW + " TEXT");
            }
            if (!columns.contains(COL_DATE_ADDED)) {
                db.execSQL("ALTER TABLE " + TABLE_MEDIA + " ADD COLUMN " + COL_DATE_ADDED + " DATETIME");
                db.execSQL("UPDATE " + TABLE_MEDIA + " SET " + COL_DATE_ADDED + " = CURRENT_TIMESTAMP WHERE " + COL_DATE_ADDED + " IS NULL");
            }
            if (!columns.contains(COL_LAST_UPDATED)) {
                db.execSQL("ALTER TABLE " + TABLE_MEDIA + " ADD COLUMN " + COL_LAST_UPDATED + " DATETIME");
                db.execSQL("UPDATE " + TABLE_MEDIA + " SET " + COL_LAST_UPDATED + " = CURRENT_TIMESTAMP WHERE " + COL_LAST_UPDATED + " IS NULL");
            }
            if (!columns.contains(COL_IS_FAVORITE)) {
                db.execSQL("ALTER TABLE " + TABLE_MEDIA + " ADD COLUMN " + COL_IS_FAVORITE + " INTEGER DEFAULT 0");
            }
            
            createTrigger(db);
        }
        if (oldVersion < 9) {
            Set<String> columns = getTableColumns(db, TABLE_MEDIA);
            if (!columns.contains(COL_JOURNAL)) {
                db.execSQL("ALTER TABLE " + TABLE_MEDIA + " ADD COLUMN " + COL_JOURNAL + " TEXT");
            }
            if (!columns.contains(COL_MOOD)) {
                db.execSQL("ALTER TABLE " + TABLE_MEDIA + " ADD COLUMN " + COL_MOOD + " TEXT");
            }
            if (!columns.contains(COL_PRIORITY)) {
                db.execSQL("ALTER TABLE " + TABLE_MEDIA + " ADD COLUMN " + COL_PRIORITY + " TEXT DEFAULT 'Medium'");
                db.execSQL("UPDATE " + TABLE_MEDIA + " SET " + COL_PRIORITY + " = 'Medium' WHERE " + COL_PRIORITY + " IS NULL OR " + COL_PRIORITY + " = ''");
            }
        }
        if (oldVersion < 10) {
            // Fix the progress_log table schema - the DEFAULT date function had incorrect syntax
            // Recreate the table with the corrected DEFAULT clause
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_PROGRESS_LOG);
            String createLogTable = "CREATE TABLE " + TABLE_PROGRESS_LOG + " (" +
                    COL_LOG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_LOG_MEDIA_ID + " INTEGER NOT NULL, " +
                    COL_PROGRESS_ADDED + " INTEGER NOT NULL, " +
                    COL_LOG_DATE + " DATE DEFAULT (date('now', 'localtime')), " +
                    "FOREIGN KEY (" + COL_LOG_MEDIA_ID + ") REFERENCES " + TABLE_MEDIA + "(" + COL_ID + ") ON DELETE CASCADE)";
            db.execSQL(createLogTable);
        }
        if (oldVersion < 11) {
            backfillProgressLogsFromCurrentProgress(db);
        }
        if (oldVersion < 12) {
            Set<String> columns = getTableColumns(db, TABLE_MEDIA);
            if (!columns.contains(COL_DESCRIPTION)) {
                db.execSQL("ALTER TABLE " + TABLE_MEDIA + " ADD COLUMN " + COL_DESCRIPTION + " TEXT");
            }
        }
    }

    private Set<String> getTableColumns(SQLiteDatabase db, String tableName) {
        Set<String> columns = new HashSet<>();
        Cursor cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(1));
            }
            cursor.close();
        }
        return columns;
    }

    // --- CRUD OPERATIONS ---
    public long addMedia(String title, String type, String genre, String creator, int totalCount, String unit, String runtime, String imagePath, String description) {
        if (imagePath != null && imagePath.trim().isEmpty()) {
            imagePath = null;
        }
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_TITLE, title);
        values.put(COL_MEDIA_TYPE, type);
        values.put(COL_GENRE, genre);
        values.put(COL_CREATOR, creator);
        values.put(COL_TOTAL_COUNT, totalCount);
        values.put(COL_UNIT, unit);
        values.put(COL_RUNTIME, runtime);
        values.put(COL_IMAGE_PATH, imagePath);
        values.put(COL_DESCRIPTION, description);
        long result = db.insertWithOnConflict(TABLE_MEDIA, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        db.close();
        return result;
    }

    public Cursor getAllMedia() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_MEDIA + " WHERE " + COL_STATUS + " != 'Recently Deleted' ORDER BY " + COL_LAST_UPDATED + " DESC", null);
    }

    public Cursor getAllMediaIncludingTrash() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_MEDIA + " ORDER BY " + COL_LAST_UPDATED + " DESC", null);
    }

    public Cursor getMediaById(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_MEDIA + " WHERE " + COL_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public boolean updateMedia(int id, String title, String type, String genre, String status, int progress, int total, String unit, String imagePath, float rating, String review) {
        SQLiteDatabase db = this.getWritableDatabase();
        int oldProgress = getCurrentProgress(db, id);
        ContentValues values = new ContentValues();
        values.put(COL_TITLE, title);
        values.put(COL_MEDIA_TYPE, type);
        values.put(COL_GENRE, genre);
        values.put(COL_STATUS, status);
        values.put(COL_CURRENT_PROGRESS, progress);
        values.put(COL_TOTAL_COUNT, total);
        values.put(COL_UNIT, unit);
        values.put(COL_IMAGE_PATH, imagePath);
        values.put(COL_RATING, rating);
        values.put(COL_REVIEW, review);
        if (progress > oldProgress) {
            logProgressDelta(db, id, progress - oldProgress);
        }
        int result = db.update(TABLE_MEDIA, values, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
        return result > 0;
    }

    public boolean updateMedia(int id, String title, String type, String genre, String status, int progress, int total, String unit, String imagePath, float rating, String review, String journal, String mood, String priority, boolean isFavorite) {
        SQLiteDatabase db = this.getWritableDatabase();
        int oldProgress = getCurrentProgress(db, id);
        ContentValues values = new ContentValues();
        values.put(COL_TITLE, title);
        values.put(COL_MEDIA_TYPE, type);
        values.put(COL_GENRE, genre);
        values.put(COL_STATUS, status);
        values.put(COL_CURRENT_PROGRESS, progress);
        values.put(COL_TOTAL_COUNT, total);
        values.put(COL_UNIT, unit);
        values.put(COL_IMAGE_PATH, imagePath);
        values.put(COL_RATING, rating);
        values.put(COL_REVIEW, review);
        values.put(COL_JOURNAL, journal);
        values.put(COL_MOOD, mood);
        values.put(COL_PRIORITY, priority == null || priority.isEmpty() ? "Medium" : priority);
        values.put(COL_IS_FAVORITE, isFavorite ? 1 : 0);
        if (progress > oldProgress) {
            logProgressDelta(db, id, progress - oldProgress);
        }
        int result = db.update(TABLE_MEDIA, values, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
        return result > 0;
    }

    public boolean updateProgress(int id, int newProgress, String newStatus, float newRating) {
        SQLiteDatabase db = this.getWritableDatabase();
        
        int oldProgress = 0;
        Cursor cursor = db.query(TABLE_MEDIA, new String[]{COL_CURRENT_PROGRESS}, 
                COL_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                oldProgress = cursor.getInt(0);
            }
            cursor.close();
        }

        int progressAdded = newProgress - oldProgress;

        if (progressAdded > 0) {
            logProgressDelta(db, id, progressAdded);
        }

        ContentValues values = new ContentValues();
        values.put(COL_CURRENT_PROGRESS, newProgress);
        values.put(COL_STATUS, newStatus);
        values.put(COL_RATING, newRating);
        values.put(COL_LAST_UPDATED, getDateTime());
        int result = db.update(TABLE_MEDIA, values, COL_ID + "=?", new String[]{String.valueOf(id)});
        
        db.close();
        return result > 0;
    }

    public boolean deleteMedia(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_MEDIA, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
        return result > 0;
    }

    public boolean updateImagePath(int id, String newPath) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_IMAGE_PATH, newPath);
        int result = db.update(TABLE_MEDIA, values, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
        return result > 0;
    }

    // --- DASHBOARD METRICS QUERIES ---

    public int getDailyPages() {
        return getProgressSumWithFallback(new String[]{"Pages", "Chapters"}, "date('now', 'localtime')");
    }

    public int getWeeklyPages() {
        return getProgressSumWithFallback(new String[]{"Pages", "Chapters"}, "date('now', 'localtime', '-7 days')");
    }

    public int getMonthlyPages() {
        return getProgressSumWithFallback(new String[]{"Pages", "Chapters"}, "date('now', 'localtime', '-30 days')");
    }

    public int getDailyMinutes() {
        return getProgressSumWithFallback(new String[]{"Minutes"}, "date('now', 'localtime')");
    }

    public int getWeeklyMinutes() {
        return getProgressSumWithFallback(new String[]{"Minutes"}, "date('now', 'localtime', '-7 days')");
    }

    public int getDailyEpisodes() {
        return getProgressSumWithFallback(new String[]{"Episodes"}, "date('now', 'localtime')");
    }

    public int getWeeklyEpisodes() {
        return getProgressSumWithFallback(new String[]{"Episodes"}, "date('now', 'localtime', '-7 days')");
    }

    public int getMonthlyMinutes() {
        return getProgressSumWithFallback(new String[]{"Minutes"}, "date('now', 'localtime', '-30 days')");
    }

    public int getMonthlyEpisodes() {
        return getProgressSumWithFallback(new String[]{"Episodes"}, "date('now', 'localtime', '-30 days')");
    }

    private int getProgressSumWithFallback(String[] units, String dateFilter) {
        SQLiteDatabase db = this.getReadableDatabase();
        String inClause = buildInClause(units.length);
        String logQuery = "SELECT COALESCE(SUM(l." + COL_PROGRESS_ADDED + "), 0) FROM " + TABLE_PROGRESS_LOG + " l " +
                "JOIN " + TABLE_MEDIA + " m ON l." + COL_LOG_MEDIA_ID + " = m." + COL_ID + " " +
                "WHERE m." + COL_UNIT + " IN (" + inClause + ") " +
                "AND l." + COL_LOG_DATE + " >= " + dateFilter + " " +
                "AND m." + COL_STATUS + " != 'Recently Deleted'";

        Cursor logCursor = db.rawQuery(logQuery, units);
        int totalFromLog = 0;
        if (logCursor.moveToFirst()) {
            totalFromLog = logCursor.getInt(0);
        }
        logCursor.close();
        if (totalFromLog > 0) {
            return totalFromLog;
        }

        String fallbackQuery = "SELECT COALESCE(SUM(" + COL_CURRENT_PROGRESS + "), 0) FROM " + TABLE_MEDIA + " " +
                "WHERE " + COL_UNIT + " IN (" + inClause + ") " +
                "AND " + COL_STATUS + " != 'Recently Deleted' " +
                "AND date(" + COL_LAST_UPDATED + ") >= " + dateFilter;
        Cursor fallbackCursor = db.rawQuery(fallbackQuery, units);
        int totalFallback = 0;
        if (fallbackCursor.moveToFirst()) {
            totalFallback = fallbackCursor.getInt(0);
        }
        fallbackCursor.close();
        return totalFallback;
    }

    public int getTotalMinutesWatched() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT " +
                "SUM(CASE " +
                "  WHEN " + COL_UNIT + " = 'Minutes' THEN " + COL_CURRENT_PROGRESS + " " +
                "  WHEN " + COL_UNIT + " = 'Episodes' THEN " + COL_CURRENT_PROGRESS + " * 24 " +
                "  WHEN (" + COL_MEDIA_TYPE + " = 'Movie' OR " + COL_MEDIA_TYPE + " = 'Series') AND " + COL_UNIT + " NOT IN ('Minutes', 'Episodes') THEN " + COL_CURRENT_PROGRESS + " * 120 " +
                "  ELSE 0 END) " +
                "FROM " + TABLE_MEDIA + " WHERE " + COL_STATUS + " != 'Recently Deleted'";
        
        int total = 0;
        try (Cursor cursor = db.rawQuery(query, null);) {
            if (cursor != null && cursor.moveToFirst()) total = cursor.getInt(0);
        }
        return total;
    }

    public int getTotalPagesRead() {
        SQLiteDatabase db = this.getReadableDatabase();
        int total = 0;
        try (Cursor cursor = db.rawQuery("SELECT SUM(" + COL_CURRENT_PROGRESS + ") FROM " + TABLE_MEDIA + " WHERE " + COL_UNIT + " IN ('Pages', 'Chapters') AND " + COL_STATUS + " != 'Recently Deleted'", null);) {
            if (cursor != null && cursor.moveToFirst()) total = cursor.getInt(0);
        }
        return total;
    }

    public int getTotalEpisodesWatched() {
        SQLiteDatabase db = this.getReadableDatabase();
        int total = 0;
        try (Cursor cursor = db.rawQuery("SELECT SUM(" + COL_CURRENT_PROGRESS + ") FROM " + TABLE_MEDIA + " WHERE " + COL_UNIT + " = 'Episodes' AND " + COL_STATUS + " != 'Recently Deleted'", null);) {
            if (cursor != null && cursor.moveToFirst()) total = cursor.getInt(0);
        }
        return total;
    }

    public int getStatusCount(String status) {
        SQLiteDatabase db = this.getReadableDatabase();
        int count = 0;
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_MEDIA + " WHERE " + COL_STATUS + " = ?", new String[]{status});) {
            if (cursor != null && cursor.moveToFirst()) count = cursor.getInt(0);
        }
        return count;
    }

    public float getAverageRating() {
        SQLiteDatabase db = this.getReadableDatabase();
        float avg = 0f;
        try (Cursor cursor = db.rawQuery("SELECT AVG(" + COL_RATING + ") FROM " + TABLE_MEDIA + " WHERE " + COL_RATING + " > 0 AND " + COL_STATUS + " != 'Recently Deleted'", null);) {
            if (cursor != null && cursor.moveToFirst()) avg = cursor.getFloat(0);
        }
        return avg;
    }

    public int getCompletedCountByType(String type) {
        SQLiteDatabase db = this.getReadableDatabase();
        int count = 0;
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_MEDIA + " WHERE " + COL_MEDIA_TYPE + " = ? AND " + COL_STATUS + " = 'Completed'", new String[]{type});) {
            if (cursor != null && cursor.moveToFirst()) count = cursor.getInt(0);
        }
        return count;
    }

    public String getTopGenre() {
        java.util.Map<String, Integer> counts = getGenreCounts();
        String topGenre = "N/A";
        int highest = 0;
        for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > highest) {
                highest = entry.getValue();
                topGenre = entry.getKey();
            }
        }
        return topGenre;
    }

    public java.util.Map<String, Integer> getGenreCounts() {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + COL_GENRE + " FROM " + TABLE_MEDIA + " WHERE " + COL_STATUS + " != 'Recently Deleted'", null);
        if (cursor.moveToFirst()) {
            do {
                String rawGenre = cursor.getString(0);
                addGenreTokens(counts, rawGenre);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return counts;
    }

    private void addGenreTokens(java.util.Map<String, Integer> counts, String rawGenre) {
        if (rawGenre == null || rawGenre.trim().isEmpty()) {
            counts.put("Uncategorized", counts.getOrDefault("Uncategorized", 0) + 1);
            return;
        }
        String[] genres = rawGenre.split(",");
        for (String token : genres) {
            String normalized = token.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            counts.put(normalized, counts.getOrDefault(normalized, 0) + 1);
        }
    }

    public boolean addCollectionTag(int mediaId, String collectionName) {
        if (collectionName == null || collectionName.trim().isEmpty()) {
            return false;
        }
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COL_GENRE + " FROM " + TABLE_MEDIA + " WHERE " + COL_ID + "=?",
                new String[]{String.valueOf(mediaId)}
        );
        if (!cursor.moveToFirst()) {
            cursor.close();
            db.close();
            return false;
        }
        String existing = cursor.getString(0);
        cursor.close();

        List<String> parts = new ArrayList<>();
        if (existing != null && !existing.trim().isEmpty()) {
            for (String token : existing.split(",")) {
                String normalized = token.trim();
                if (!normalized.isEmpty()) {
                    parts.add(normalized);
                }
            }
        }

        String normalizedCollection = collectionName.trim();
        boolean exists = false;
        for (String token : parts) {
            if (token.equalsIgnoreCase(normalizedCollection)) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            parts.add(normalizedCollection);
        }

        StringBuilder merged = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                merged.append(", ");
            }
            merged.append(parts.get(i));
        }

        ContentValues values = new ContentValues();
        values.put(COL_GENRE, merged.toString());
        values.put(COL_LAST_UPDATED, getDateTime());
        int rows = db.update(TABLE_MEDIA, values, COL_ID + "=?", new String[]{String.valueOf(mediaId)});
        db.close();
        return rows > 0;
    }

    public int getTotalCountByType(String type) {
        SQLiteDatabase db = this.getReadableDatabase();
        int count = 0;
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_MEDIA + " WHERE " + COL_MEDIA_TYPE + " = ? AND " + COL_STATUS + " != 'Recently Deleted'", new String[]{type});) {
            if (cursor != null && cursor.moveToFirst()) count = cursor.getInt(0);
        }
        return count;
    }

    public Cursor getRandomPlanningMedia() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_MEDIA + " WHERE " + COL_STATUS + " = 'Planning' ORDER BY RANDOM() LIMIT 1", null);
    }

    public Cursor getRandomPlanningMediaWeighted() {
        return getRandomPlanningMediaWeighted(null, null);
    }

    public Cursor getRandomPlanningMediaWeighted(String mediaTypeFilter, String genreFilter) {
        SQLiteDatabase db = this.getReadableDatabase();
        StringBuilder queryBuilder = new StringBuilder("SELECT * FROM " + TABLE_MEDIA + " WHERE " + COL_STATUS + " = 'Planning' ");
        List<String> args = new ArrayList<>();
        if (mediaTypeFilter != null && !mediaTypeFilter.trim().isEmpty()) {
            queryBuilder.append("AND ").append(COL_MEDIA_TYPE).append(" = ? ");
            args.add(mediaTypeFilter.trim());
        }
        if (genreFilter != null && !genreFilter.trim().isEmpty()) {
            queryBuilder.append("AND ").append(COL_GENRE).append(" LIKE ? ");
            args.add("%" + genreFilter.trim() + "%");
        }
        queryBuilder.append(
                "ORDER BY (ABS(RANDOM()) / 2147483647.0) / " +
                "CASE " + COL_PRIORITY + " WHEN 'High' THEN 3.0 WHEN 'Medium' THEN 1.7 ELSE 1.0 END LIMIT 1"
        );
        return db.rawQuery(queryBuilder.toString(), args.toArray(new String[0]));
    }

    public List<String> getPlanningGenres() {
        List<String> genres = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT DISTINCT " + COL_GENRE + " FROM " + TABLE_MEDIA + " " +
                "WHERE " + COL_STATUS + " != 'Recently Deleted' AND " + COL_GENRE + " IS NOT NULL AND TRIM(" + COL_GENRE + ") != '' " +
                "ORDER BY " + COL_GENRE + " COLLATE NOCASE";
        Cursor cursor = db.rawQuery(query, null);
        if (cursor.moveToFirst()) {
            do {
                String rawGenre = cursor.getString(0);
                if (rawGenre == null) {
                    continue;
                }
                String[] parts = rawGenre.split(",");
                for (String part : parts) {
                    String normalized = part.trim();
                    if (!normalized.isEmpty() && !genres.contains(normalized)) {
                        genres.add(normalized);
                    }
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        return genres;
    }

    public List<String> getShakeGenresByType(String mediaTypeFilter) {
        List<String> genres = new ArrayList<>();
        LinkedHashSet<String> uniqueGenres = new LinkedHashSet<>();
        SQLiteDatabase db = this.getReadableDatabase();
        StringBuilder queryBuilder = new StringBuilder(
                "SELECT " + COL_GENRE + " FROM " + TABLE_MEDIA + " " +
                        "WHERE " + COL_STATUS + " NOT IN ('Ongoing', 'Completed', 'Recently Deleted') " +
                        "AND " + COL_GENRE + " IS NOT NULL AND TRIM(" + COL_GENRE + ") != '' "
        );
        List<String> args = new ArrayList<>();
        if (mediaTypeFilter != null && !mediaTypeFilter.trim().isEmpty()) {
            queryBuilder.append("AND ").append(COL_MEDIA_TYPE).append(" = ? ");
            args.add(mediaTypeFilter.trim());
        }
        queryBuilder.append("ORDER BY ").append(COL_GENRE).append(" COLLATE NOCASE");

        Cursor cursor = db.rawQuery(queryBuilder.toString(), args.toArray(new String[0]));
        if (cursor.moveToFirst()) {
            do {
                String rawGenre = cursor.getString(0);
                if (rawGenre == null) {
                    continue;
                }
                String[] parts = rawGenre.split(",");
                for (String part : parts) {
                    String normalized = part.trim();
                    if (!normalized.isEmpty()) {
                        uniqueGenres.add(normalized);
                    }
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        genres.addAll(uniqueGenres);
        return genres;
    }

    public boolean updateMediaMetadata(int id, String review, String journal, String mood, String priority, boolean isFavorite) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_REVIEW, review);
        values.put(COL_JOURNAL, journal);
        values.put(COL_MOOD, mood);
        values.put(COL_PRIORITY, priority == null || priority.isEmpty() ? "Medium" : priority);
        values.put(COL_IS_FAVORITE, isFavorite ? 1 : 0);
        int result = db.update(TABLE_MEDIA, values, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
        return result > 0;
    }

    public Cursor getHighestProgressOngoing() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_MEDIA + " WHERE " + COL_STATUS + " = 'Ongoing' AND " + COL_TOTAL_COUNT + " > 0 " +
                "ORDER BY (1.0 * " + COL_CURRENT_PROGRESS + " / " + COL_TOTAL_COUNT + ") DESC, " + COL_LAST_UPDATED + " DESC LIMIT 1";
        return db.rawQuery(query, null);
    }

    public boolean incrementProgressByOne(int mediaId) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + COL_CURRENT_PROGRESS + ", " + COL_TOTAL_COUNT + ", " + COL_STATUS + ", " + COL_RATING +
                " FROM " + TABLE_MEDIA + " WHERE " + COL_ID + "=?", new String[]{String.valueOf(mediaId)});
        if (cursor == null || !cursor.moveToFirst()) {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
            return false;
        }

        int current = cursor.getInt(0);
        int total = cursor.getInt(1);
        String status = cursor.getString(2);
        float rating = cursor.getFloat(3);
        cursor.close();

        int newProgress = Math.min(current + 1, total);
        String newStatus = newProgress >= total ? "Completed" : status;
        ContentValues values = new ContentValues();
        values.put(COL_CURRENT_PROGRESS, newProgress);
        values.put(COL_STATUS, newStatus);
        values.put(COL_RATING, rating);
        int updatedRows = db.update(TABLE_MEDIA, values, COL_ID + "=?", new String[]{String.valueOf(mediaId)});
        db.close();
        return updatedRows > 0;
    }

    public Cursor getTopFavoritesForQr(int limit) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT " + COL_TITLE + ", " + COL_MEDIA_TYPE + ", " + COL_GENRE + ", " + COL_TOTAL_COUNT + ", " + COL_UNIT + ", " +
                        COL_CURRENT_PROGRESS + ", " + COL_STATUS + ", " + COL_PRIORITY + ", " + COL_RATING +
                        " FROM " + TABLE_MEDIA + " WHERE " + COL_IS_FAVORITE + " = 1 ORDER BY " + COL_RATING + " DESC, " + COL_LAST_UPDATED + " DESC LIMIT ?",
                new String[]{String.valueOf(limit)});
    }

    public long addImportedBacklogItem(String title, String mediaType, String genre, int totalCount, String unit, String priority) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_TITLE, title);
        values.put(COL_MEDIA_TYPE, mediaType);
        values.put(COL_GENRE, genre);
        values.put(COL_TOTAL_COUNT, Math.max(totalCount, 1));
        values.put(COL_UNIT, (unit == null || unit.isEmpty()) ? "Episodes" : unit);
        values.put(COL_STATUS, "Planning");
        values.put(COL_PRIORITY, (priority == null || priority.isEmpty()) ? "Medium" : priority);
        long result = db.insertWithOnConflict(TABLE_MEDIA, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        db.close();
        return result;
    }

    public Cursor getBacklogSpotlightMedia() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_MEDIA + " " +
                "WHERE " + COL_STATUS + " IN ('Planning', 'Ongoing') " +
                "ORDER BY CASE " + COL_STATUS + " " +
                "WHEN 'Ongoing' THEN 0 " +
                "WHEN 'Planning' THEN 1 ELSE 2 END, " +
                COL_LAST_UPDATED + " DESC LIMIT 1";
        return db.rawQuery(query, null);
    }

    public Cursor getOngoingMediaList() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_MEDIA + " " +
                "WHERE " + COL_STATUS + " = 'Ongoing' " +
                "ORDER BY " + COL_LAST_UPDATED + " DESC LIMIT 5";
        return db.rawQuery(query, null);
    }

    public int getDailyProgress(String unit, String datePattern) {
        SQLiteDatabase db = this.getReadableDatabase();
        // datePattern should be "yyyy-MM-dd"
        // We look for log_date starting with this pattern
        String query = "SELECT SUM(p." + COL_PROGRESS_ADDED + ") FROM " + TABLE_PROGRESS_LOG + " p " +
                "JOIN " + TABLE_MEDIA + " m ON p." + COL_LOG_MEDIA_ID + " = m." + COL_ID + " " +
                "WHERE m." + COL_UNIT + " = ? AND p." + COL_LOG_DATE + " LIKE ?";
        
        Cursor cursor = db.rawQuery(query, new String[]{unit, datePattern + "%"});
        int total = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                total = cursor.getInt(0);
            }
            cursor.close();
        }
        return total;
    }

    public void seedDatabase() {
        SQLiteDatabase db = this.getWritableDatabase();
        
        // Clear existing data to ensure clean state
        db.execSQL("DELETE FROM " + TABLE_PROGRESS_LOG);
        db.execSQL("DELETE FROM " + TABLE_MEDIA);

        Object[][] mediaData = {
            // Movies
            {"Inception", "Movie", "Sci-Fi", "Christopher Nolan", 148, "Minutes", "Dream within a dream heist."},
            {"The Matrix", "Movie", "Sci-Fi", "The Wachowskis", 136, "Minutes", "Reality is a simulation."},
            {"Interstellar", "Movie", "Sci-Fi", "Christopher Nolan", 169, "Minutes", "Space travel to save humanity."},
            {"Joker", "Movie", "Drama", "Todd Phillips", 122, "Minutes", "Origin story of the iconic villain."},
            {"Pulp Fiction", "Movie", "Crime", "Quentin Tarantino", 154, "Minutes", "Intertwining criminal lives."},
            {"Gladiator", "Movie", "Action", "Ridley Scott", 155, "Minutes", "A betrayed general seeks revenge."},
            {"Avatar", "Movie", "Sci-Fi", "James Cameron", 162, "Minutes", "Human on an alien planet."},
            {"The Dark Knight", "Movie", "Action", "Christopher Nolan", 152, "Minutes", "Batman faces the Joker."},
            {"Spirited Away", "Movie", "Fantasy", "Hayao Miyazaki", 125, "Minutes", "Girl trapped in spirit world."},
            {"Parasite", "Movie", "Thriller", "Bong Joon-ho", 132, "Minutes", "Class struggle in Seoul."},
            {"Everything Everywhere All At Once", "Movie", "Sci-Fi", "Daniels", 139, "Minutes", "Multiverse madness."},
            {"Dune: Part Two", "Movie", "Sci-Fi", "Denis Villeneuve", 166, "Minutes", "Paul Atreides unites with the Fremen."},
            {"Oppenheimer", "Movie", "Biography", "Christopher Nolan", 180, "Minutes", "The story of American scientist J. Robert Oppenheimer."},
            {"Spider-Man: Into the Spider-Verse", "Movie", "Animation", "Bob Persichetti", 117, "Minutes", "Teen Miles Morales becomes the Spider-Man."},
            
            // Series
            {"Breaking Bad", "Series", "Drama", "Vince Gilligan", 62, "Episodes", "Chemistry teacher turns to crime."},
            {"Stranger Things", "Series", "Sci-Fi", "The Duffer Brothers", 42, "Episodes", "Supernatural mysteries in a small town."},
            {"The Office", "Series", "Comedy", "Greg Daniels", 201, "Episodes", "Daily lives of office employees."},
            {"Friends", "Series", "Comedy", "David Crane", 236, "Episodes", "Six friends living in Manhattan."},
            {"The Witcher", "Series", "Fantasy", "Lauren Schmidt Hissrich", 24, "Episodes", "Monster hunter Geralt of Rivia."},
            {"Mandalorian", "Series", "Sci-Fi", "Jon Favreau", 24, "Episodes", "Bounty hunter in the Star Wars universe."},
            {"Game of Thrones", "Series", "Fantasy", "David Benioff", 73, "Episodes", "Noble families vie for control."},
            {"Arcane", "Series", "Sci-Fi", "Christian Linke", 9, "Episodes", "Origins of two iconic League champions."},
            {"The Last of Us", "Series", "Drama", "Craig Mazin", 9, "Episodes", "Survival in a post-apocalyptic world."},
            {"Succession", "Series", "Drama", "Jesse Armstrong", 39, "Episodes", "Power struggle in a media empire."},
            {"Severance", "Series", "Sci-Fi", "Dan Erickson", 9, "Episodes", "Memories surgically divided between work and life."},
            {"The Bear", "Series", "Drama", "Christopher Storer", 18, "Episodes", "Chef returns to run family sandwich shop."},
            
            // Anime
            {"One Piece", "Anime", "Adventure", "Eiichiro Oda", 1100, "Episodes", "Monkey D. Luffy seeks the pirate treasure."},
            {"Naruto", "Anime", "Action", "Masashi Kishimoto", 720, "Episodes", "Ninja seeking recognition and leadership."},
            {"Attack on Titan", "Anime", "Fantasy", "Hajime Isayama", 89, "Episodes", "Humanity fights giant titans."},
            {"Demon Slayer", "Anime", "Action", "Koyoharu Gotouge", 55, "Episodes", "Boy fights demons to save his sister."},
            {"Death Note", "Anime", "Thriller", "Tsugumi Ohba", 37, "Episodes", "Student finds a notebook that kills."},
            {"Bleach", "Anime", "Action", "Tite Kubo", 366, "Episodes", "Soul Reaper protecting humans from spirits."},
            {"Your Name", "Anime", "Romance", "Makoto Shinkai", 1, "Episodes", "Two teens swap bodies mysteriously."},
            {"Fullmetal Alchemist: Brotherhood", "Anime", "Adventure", "Hiromu Arakawa", 64, "Episodes", "Brothers search for Philosopher's Stone."},
            {"Cyberpunk: Edgerunners", "Anime", "Sci-Fi", "Rafal Jaki", 10, "Episodes", "Street kid tries to survive in Night City."},
            {"Spy x Family", "Anime", "Comedy", "Tatsuya Endo", 37, "Episodes", "Spy, assassin, and telepath form a family."},
            {"Jujutsu Kaisen", "Anime", "Action", "Gege Akutami", 47, "Episodes", "Student joins occult organization."},
            {"Chainsaw Man", "Anime", "Action", "Tatsuki Fujimoto", 12, "Episodes", "Devil hunter with chainsaw."},
            {"Frieren: Beyond Journey's End", "Anime", "Fantasy", "Kanehito Yamada", 28, "Episodes", "Elf mage's journey after defeating demon king."},
            
            // Manga
            {"Berserk", "Manga", "Horror", "Kentaro Miura", 373, "Chapters", "The journey of Guts, a lone mercenary."},
            {"Solo Leveling", "Manga", "Action", "Chugong", 179, "Chapters", "Weak hunter becomes the strongest."},
            {"Dragon Ball", "Manga", "Action", "Akira Toriyama", 519, "Chapters", "Goku's quest for the Dragon Balls."},
            {"Chainsaw Man", "Manga", "Action", "Tatsuki Fujimoto", 150, "Chapters", "Devil hunter with a chainsaw heart."},
            {"One Punch Man", "Manga", "Action", "ONE", 195, "Chapters", "Hero who defeats everyone with one punch."},
            {"Vagabond", "Manga", "Historical", "Takehiko Inoue", 327, "Chapters", "Life of samurai Musashi Miyamoto."},
            {"Monster", "Manga", "Thriller", "Naoki Urasawa", 162, "Chapters", "Doctor saves a boy who becomes a monster."},
            
            // Books
            {"1984", "Book", "Science Fiction", "George Orwell", 328, "Pages", "Totalitarianism and government surveillance."},
            {"The Hobbit", "Book", "Fantasy", "J.R.R. Tolkien", 310, "Pages", "Bilbo Baggins' unexpected adventure."},
            {"Harry Potter", "Book", "Fantasy", "J.K. Rowling", 309, "Pages", "Young wizard's journey at Hogwarts."},
            {"Dune", "Book", "Sci-Fi", "Frank Herbert", 412, "Pages", "Political struggle on a desert planet."},
            {"The Great Gatsby", "Book", "Literary Fiction", "F. Scott Fitzgerald", 180, "Pages", "Wealth, love, and the American dream."},
            {"Sherlock Holmes", "Book", "Mystery", "Arthur Conan Doyle", 350, "Pages", "Famous detective solving crimes."},
            {"Dracula", "Book", "Horror", "Bram Stoker", 418, "Pages", "The original vampire count."},
            {"Atomic Habits", "Book", "Self-Help", "James Clear", 320, "Pages", "Tiny changes, remarkable results."},
            {"Project Hail Mary", "Book", "Sci-Fi", "Andy Weir", 496, "Pages", "Lone astronaut must save humanity."},
            {"The Midnight Library", "Book", "Fantasy", "Matt Haig", 304, "Pages", "Library between life and death."},
            {"Fourth Wing", "Book", "Fantasy", "Rebecca Yarros", 500, "Pages", "Dragon riders academy."},
            {"Mistborn: The Final Empire", "Book", "Fantasy", "Brandon Sanderson", 541, "Pages", "Heist to overthrow a god-emperor."}
        };

        String[] statuses = {"Ongoing", "Completed", "Planning", "Dropped"};
        String[] moods = {"Excited", "Happy", "Neutral", "Sad", "Mind-blown"};
        String[] priorities = {"High", "Medium", "Low"};
        
        int index = 0;
        for (Object[] row : mediaData) {
            ContentValues v = new ContentValues();
            String title = (String) row[0];
            String type = (String) row[1];
            String baseGenre = (String) row[2];
            String creator = (String) row[3];
            int total = (int) row[4];
            String unit = (String) row[5];
            String desc = (String) row[6];
            
            String status = statuses[index % statuses.length];
            int progress;
            if ("Completed".equals(status)) {
                progress = total;
            } else if ("Planning".equals(status)) {
                progress = 0;
            } else if ("Dropped".equals(status)) {
                progress = Math.max(1, total / 10);
            } else {
                progress = Math.max(1, (int) (total * 0.45f));
            }

            String genre = baseGenre;
            if ("Sci-Fi".equalsIgnoreCase(baseGenre) && index % 2 == 0) {
                genre = "Sci-Fi, Adventure";
            } else if ("Fantasy".equalsIgnoreCase(baseGenre) && index % 3 == 0) {
                genre = "Fantasy, Adventure";
            }
            String priority = priorities[index % priorities.length];
            String mood = moods[index % moods.length];
            boolean isFavorite = index % 5 == 0;

            v.put(COL_TITLE, title); // Removed (Demo) suffix for cleaner look
            v.put(COL_MEDIA_TYPE, type);
            v.put(COL_GENRE, genre);
            v.put(COL_CREATOR, creator);
            v.put(COL_TOTAL_COUNT, total);
            v.put(COL_UNIT, unit);
            v.put(COL_STATUS, status);
            v.put(COL_CURRENT_PROGRESS, progress);
            v.put(COL_RATING, 3.0f + (float)(Math.random() * 2.0f));
            v.put(COL_DESCRIPTION, desc);
            v.put(COL_PRIORITY, priority);
            v.put(COL_MOOD, mood);
            v.put(COL_IS_FAVORITE, isFavorite ? 1 : 0);
            if ("Completed".equals(status)) {
                v.put(COL_REVIEW, "Finished and recommended.");
                v.put(COL_JOURNAL, "Great pacing and memorable moments.");
            } else if ("Dropped".equals(status)) {
                v.put(COL_REVIEW, "Paused for now.");
                v.put(COL_JOURNAL, "Might return later.");
            }

            long mediaId = db.insertWithOnConflict(TABLE_MEDIA, null, v, SQLiteDatabase.CONFLICT_IGNORE);
            if (mediaId != -1 && progress > 0) {
                seedProgressHistory(db, (int) mediaId, progress);
            }
            index++;
        }
        db.close();
    }

    public void clearAllMedia() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_PROGRESS_LOG);
        db.execSQL("DELETE FROM " + TABLE_MEDIA);
        db.close();
    }

    private String getDateTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        Date date = new Date();
        return dateFormat.format(date);
    }

    private int getCurrentProgress(SQLiteDatabase db, int id) {
        int oldProgress = 0;
        Cursor cursor = db.query(TABLE_MEDIA, new String[]{COL_CURRENT_PROGRESS},
                COL_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                oldProgress = cursor.getInt(0);
            }
            cursor.close();
        }
        return oldProgress;
    }

    private void logProgressDelta(SQLiteDatabase db, int mediaId, int delta) {
        if (delta <= 0) {
            return;
        }
        ContentValues logValues = new ContentValues();
        logValues.put(COL_LOG_MEDIA_ID, mediaId);
        logValues.put(COL_PROGRESS_ADDED, delta);
        db.insert(TABLE_PROGRESS_LOG, null, logValues);
    }

    private void logProgressDeltaAtDate(SQLiteDatabase db, int mediaId, int delta, String date) {
        if (delta <= 0) {
            return;
        }
        ContentValues logValues = new ContentValues();
        logValues.put(COL_LOG_MEDIA_ID, mediaId);
        logValues.put(COL_PROGRESS_ADDED, delta);
        logValues.put(COL_LOG_DATE, date);
        db.insert(TABLE_PROGRESS_LOG, null, logValues);
    }

    private void seedProgressHistory(SQLiteDatabase db, int mediaId, int progress) {
        if (progress <= 0) {
            return;
        }

        int daily = Math.max(1, progress / 5);
        int weekly = Math.max(1, progress / 3);
        int monthly = progress - daily - weekly;

        if (monthly < 1) {
            monthly = 1;
            if (weekly > 1) {
                weekly -= 1;
            } else if (daily > 1) {
                daily -= 1;
            }
        }

        int distributed = daily + weekly + monthly;
        if (distributed != progress) {
            monthly += (progress - distributed);
        }

        logProgressDeltaAtDate(db, mediaId, daily, getDateOffset(0));
        logProgressDeltaAtDate(db, mediaId, weekly, getDateOffset(-4));
        logProgressDeltaAtDate(db, mediaId, monthly, getDateOffset(-18));
    }

    private String getDateOffset(int daysOffset) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, daysOffset);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.getTime());
    }

    private void backfillProgressLogsFromCurrentProgress(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery(
                "SELECT m." + COL_ID + ", m." + COL_CURRENT_PROGRESS + " FROM " + TABLE_MEDIA + " m " +
                        "LEFT JOIN " + TABLE_PROGRESS_LOG + " l ON l." + COL_LOG_MEDIA_ID + " = m." + COL_ID + " " +
                        "WHERE m." + COL_CURRENT_PROGRESS + " > 0 " +
                        "GROUP BY m." + COL_ID + " " +
                        "HAVING COUNT(l." + COL_LOG_ID + ") = 0",
                null
        );
        if (cursor.moveToFirst()) {
            do {
                int mediaId = cursor.getInt(0);
                int progress = cursor.getInt(1);
                logProgressDelta(db, mediaId, progress);
            } while (cursor.moveToNext());
        }
        cursor.close();
    }

    private String buildInClause(int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("?");
        }
        return builder.toString();
    }

    public DailyProgress getDailyProgress(String datePattern) {
        SQLiteDatabase db = this.getReadableDatabase();
        int pages = 0;
        int episodes = 0;
        int minutes = 0;

        // Query to get sum of progress grouped by capacity_unit
        String query = "SELECT m." + COL_UNIT + ", SUM(p." + COL_PROGRESS_ADDED + ") " +
                "FROM " + TABLE_PROGRESS_LOG + " p " +
                "JOIN " + TABLE_MEDIA + " m ON p." + COL_LOG_MEDIA_ID + " = m." + COL_ID + " " +
                "WHERE p." + COL_LOG_DATE + " LIKE ? " +
                "GROUP BY m." + COL_UNIT;

        Cursor cursor = db.rawQuery(query, new String[]{datePattern + "%"});

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String unit = cursor.getString(0);
                int progress = cursor.getInt(1);

                if ("Pages".equalsIgnoreCase(unit)) {
                    pages += progress;
                } else if ("Episodes".equalsIgnoreCase(unit)) {
                    episodes += progress;
                } else if ("Minutes".equalsIgnoreCase(unit)) {
                    minutes += progress;
                }
            }
            cursor.close();
        }
        return new DailyProgress(pages, episodes, minutes);
    }
}
