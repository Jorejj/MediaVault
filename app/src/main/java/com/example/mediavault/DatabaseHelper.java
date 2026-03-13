package com.example.mediavault;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.HashSet;
import java.util.Set;

public class DatabaseHelper extends SQLiteOpenHelper {

    // Database Information
    private static final String DATABASE_NAME = "MediaVault.db";
    private static final int DATABASE_VERSION = 8;
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
                COL_TITLE + " TEXT NOT NULL, " +
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
                COL_DATE_ADDED + " DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                COL_LAST_UPDATED + " DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                COL_IS_FAVORITE + " INTEGER DEFAULT 0, " +
                "CONSTRAINT unique_title UNIQUE (" + COL_TITLE + "), " +
                "CONSTRAINT check_status CHECK (" + COL_STATUS + " IN ('Ongoing', 'Completed', 'Planning', 'Dropped')), " +
                "CONSTRAINT check_capacity_unit CHECK (" + COL_UNIT + " IN ('Pages', 'Episodes', 'Minutes', 'Chapters')), " +
                "CONSTRAINT check_user_rating CHECK (" + COL_RATING + " >= 0.0 AND " + COL_RATING + " <= 5.0), " +
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
        long result = db.insert(TABLE_MEDIA, null, values);
        db.close();
        return result;
    }

    public Cursor getAllMedia() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_MEDIA + " ORDER BY " + COL_LAST_UPDATED + " DESC", null);
    }

    public Cursor getMediaById(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_MEDIA + " WHERE " + COL_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public boolean updateMedia(int id, String title, String type, String genre, String status, int progress, int total, String unit, String imagePath, float rating, String review) {
        SQLiteDatabase db = this.getWritableDatabase();
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
            ContentValues logValues = new ContentValues();
            logValues.put(COL_LOG_MEDIA_ID, id);
            logValues.put(COL_PROGRESS_ADDED, progressAdded);
            db.insert(TABLE_PROGRESS_LOG, null, logValues);
        }

        ContentValues values = new ContentValues();
        values.put(COL_CURRENT_PROGRESS, newProgress);
        values.put(COL_STATUS, newStatus);
        values.put(COL_RATING, newRating);
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

    // --- DASHBOARD METRICS QUERIES ---

    public int getDailyPages() {
        return getProgressSum("m." + COL_UNIT + " = 'Pages' OR m." + COL_UNIT + " = 'Chapters'", "date('now', 'localtime')");
    }

    public int getWeeklyPages() {
        return getProgressSum("m." + COL_UNIT + " = 'Pages' OR m." + COL_UNIT + " = 'Chapters'", "date('now', 'localtime', '-7 days')");
    }

    public int getDailyMinutes() {
        return getProgressSum("m." + COL_UNIT + " = 'Minutes' OR m." + COL_UNIT + " = 'Episodes'", "date('now', 'localtime')");
    }

    public int getWeeklyMinutes() {
        return getProgressSum("m." + COL_UNIT + " = 'Minutes' OR m." + COL_UNIT + " = 'Episodes'", "date('now', 'localtime', '-7 days')");
    }

    private int getProgressSum(String condition, String dateFilter) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT SUM(l." + COL_PROGRESS_ADDED + ") FROM " + TABLE_PROGRESS_LOG + " l " +
                "JOIN " + TABLE_MEDIA + " m ON l." + COL_LOG_MEDIA_ID + " = m." + COL_ID + " " +
                "WHERE (" + condition + ") AND l." + COL_LOG_DATE + " >= " + dateFilter;
        
        Cursor cursor = db.rawQuery(query, null);
        int total = 0;
        if (cursor.moveToFirst()) {
            total = cursor.getInt(0);
        }
        cursor.close();
        return total;
    }

    public int getTotalMinutesWatched() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT " +
                "SUM(CASE " +
                "  WHEN " + COL_UNIT + " = 'Minutes' THEN " + COL_CURRENT_PROGRESS + " " +
                "  WHEN " + COL_UNIT + " = 'Episodes' THEN " + COL_CURRENT_PROGRESS + " * 24 " +
                "  WHEN (" + COL_MEDIA_TYPE + " = 'Movie' OR " + COL_MEDIA_TYPE + " = 'Series') AND " + COL_UNIT + " NOT IN ('Minutes', 'Episodes') THEN " + COL_CURRENT_PROGRESS + " * 120 " +
                "  ELSE 0 END) " +
                "FROM " + TABLE_MEDIA;
        
        Cursor cursor = db.rawQuery(query, null);
        int total = 0;
        if (cursor.moveToFirst()) total = cursor.getInt(0);
        cursor.close();
        return total;
    }

    public int getTotalPagesRead() {
        SQLiteDatabase db = this.getReadableDatabase();
        // Explicitly only count Pages and Chapters, or Book/Manga types
        Cursor cursor = db.rawQuery("SELECT SUM(" + COL_CURRENT_PROGRESS + ") FROM " + TABLE_MEDIA + " WHERE " + COL_UNIT + " IN ('Pages', 'Chapters')", null);
        int total = 0;
        if (cursor.moveToFirst()) total = cursor.getInt(0);
        cursor.close();
        return total;
    }

    public int getTotalEpisodesWatched() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT SUM(" + COL_CURRENT_PROGRESS + ") FROM " + TABLE_MEDIA + " WHERE " + COL_UNIT + " = 'Episodes'", null);
        int total = 0;
        if (cursor.moveToFirst()) total = cursor.getInt(0);
        cursor.close();
        return total;
    }

    public int getStatusCount(String status) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_MEDIA + " WHERE " + COL_STATUS + " = ?", new String[]{status});
        int count = 0;
        if (cursor.moveToFirst()) count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    public float getAverageRating() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT AVG(" + COL_RATING + ") FROM " + TABLE_MEDIA + " WHERE " + COL_RATING + " > 0", null);
        float avg = 0f;
        if (cursor.moveToFirst()) avg = cursor.getFloat(0);
        cursor.close();
        return avg;
    }

    public int getCompletedCountByType(String type) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_MEDIA + " WHERE " + COL_MEDIA_TYPE + " = ? AND " + COL_STATUS + " = 'Completed'", new String[]{type});
        int count = 0;
        if (cursor.moveToFirst()) count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    public String getTopGenre() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + COL_GENRE + ", COUNT(*) as count FROM " + TABLE_MEDIA + " GROUP BY " + COL_GENRE + " ORDER BY count DESC LIMIT 1", null);
        String genre = "N/A";
        if (cursor.moveToFirst()) genre = cursor.getString(0);
        cursor.close();
        return genre;
    }

    public int getTotalCountByType(String type) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_MEDIA + " WHERE " + COL_MEDIA_TYPE + " = ?", new String[]{type});
        int count = 0;
        if (cursor.moveToFirst()) count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    public void clearAllMedia() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_PROGRESS_LOG);
        db.execSQL("DELETE FROM " + TABLE_MEDIA);
        db.close();
    }
}
