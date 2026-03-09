package com.example.mediavault;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    // Database Information
    private static final String DATABASE_NAME = "MediaVault.db";
    private static final int DATABASE_VERSION = 3; 
    public static final String TABLE_MEDIA = "media_library";

    // Column Constants
    public static final String COL_ID = "media_id";
    public static final String COL_TITLE = "title";
    public static final String COL_TYPE = "media_type";
    public static final String COL_GENRE = "genre";
    public static final String COL_STATUS = "status";
    public static final String COL_PROGRESS = "current_progress";
    public static final String COL_CAPACITY = "total_capacity";
    public static final String COL_UNIT = "capacity_unit";
    public static final String COL_COVER = "cover_image_path";
    public static final String COL_RATING = "user_rating";
    public static final String COL_REVIEW = "personal_review";
    public static final String COL_DATE_ADDED = "date_added";
    public static final String COL_DATE_MODIFIED = "date_modified";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_MEDIA + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_TITLE + " TEXT NOT NULL, " +
                COL_TYPE + " TEXT NOT NULL, " +
                COL_GENRE + " TEXT, " +
                COL_STATUS + " TEXT DEFAULT 'Planning', " +
                COL_PROGRESS + " INTEGER DEFAULT 0, " +
                COL_CAPACITY + " INTEGER NOT NULL, " +
                COL_UNIT + " TEXT NOT NULL, " +
                COL_COVER + " TEXT, " +
                COL_RATING + " REAL DEFAULT 0.0, " +
                COL_REVIEW + " TEXT, " +
                COL_DATE_ADDED + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                COL_DATE_MODIFIED + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "CONSTRAINT unique_title UNIQUE (" + COL_TITLE + "), " +
                "CONSTRAINT check_status CHECK (" + COL_STATUS + " IN ('Ongoing', 'Completed', 'Planning', 'Dropped')), " +
                "CONSTRAINT check_capacity_unit CHECK (" + COL_UNIT + " IN ('Pages', 'Episodes', 'Minutes', 'Chapters')), " +
                "CONSTRAINT check_user_rating CHECK (" + COL_RATING + " >= 0.0 AND " + COL_RATING + " <= 5.0), " +
                "CONSTRAINT check_total_capacity CHECK (" + COL_CAPACITY + " > 0), " +
                "CONSTRAINT check_progress CHECK (" + COL_PROGRESS + " >= 0 AND " + COL_PROGRESS + " <= " + COL_CAPACITY + "))";
        
        db.execSQL(createTable);

        String createTrigger = "CREATE TRIGGER update_media_modtime " +
                "AFTER UPDATE ON " + TABLE_MEDIA + " " +
                "FOR EACH ROW BEGIN " +
                "UPDATE " + TABLE_MEDIA + " SET " + COL_DATE_MODIFIED + " = CURRENT_TIMESTAMP " +
                "WHERE " + COL_ID + " = OLD." + COL_ID + "; END;";
        
        db.execSQL(createTrigger);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + TABLE_MEDIA + " ADD COLUMN " + COL_REVIEW + " TEXT");
        }
    }

    public long addMedia(String title, String type, String genre, int capacity, String unit, String coverPath) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_TITLE, title);
        values.put(COL_TYPE, type);
        values.put(COL_GENRE, genre);
        values.put(COL_CAPACITY, capacity);
        values.put(COL_UNIT, unit);
        values.put(COL_COVER, coverPath);
        long result = db.insert(TABLE_MEDIA, null, values);
        db.close();
        return result;
    }

    public Cursor getAllMedia() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_MEDIA + " ORDER BY " + COL_DATE_MODIFIED + " DESC", null);
    }

    public Cursor getMediaById(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_MEDIA + " WHERE " + COL_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public boolean updateMedia(int id, String title, String type, String genre, String status, int progress, int capacity, String unit, String coverPath, float rating, String review) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_TITLE, title);
        values.put(COL_TYPE, type);
        values.put(COL_GENRE, genre);
        values.put(COL_STATUS, status);
        values.put(COL_PROGRESS, progress);
        values.put(COL_CAPACITY, capacity);
        values.put(COL_UNIT, unit);
        values.put(COL_COVER, coverPath);
        values.put(COL_RATING, rating);
        values.put(COL_REVIEW, review);
        int result = db.update(TABLE_MEDIA, values, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
        return result > 0;
    }

    public boolean updateProgress(int id, int newProgress, String newStatus, float newRating) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_PROGRESS, newProgress);
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

    // --- METRICS METHODS ---

    public int getCompletedCountByType(String type) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_MEDIA + " WHERE " + COL_TYPE + " = ? AND " + COL_STATUS + " = 'Completed'", new String[]{type});
        int count = 0;
        if (cursor.moveToFirst()) count = cursor.getInt(0);
        cursor.close();
        return count;
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

    public String getTopGenre() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + COL_GENRE + ", COUNT(" + COL_GENRE + ") as count FROM " + TABLE_MEDIA + " GROUP BY " + COL_GENRE + " ORDER BY count DESC LIMIT 1", null);
        String genre = "-";
        if (cursor.moveToFirst()) genre = cursor.getString(0);
        cursor.close();
        return genre;
    }

    public int getTotalCountByType(String type) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_MEDIA + " WHERE " + COL_TYPE + " = ?", new String[]{type});
        int count = 0;
        if (cursor.moveToFirst()) count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    public int getTotalPagesRead() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT SUM(" + COL_PROGRESS + ") FROM " + TABLE_MEDIA + " WHERE " + COL_UNIT + " = 'Pages'", null);
        int total = 0;
        if (cursor.moveToFirst()) total = cursor.getInt(0);
        cursor.close();
        return total;
    }

    public int getTotalMinutesWatched() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT SUM(" + COL_PROGRESS + ") FROM " + TABLE_MEDIA + " WHERE " + COL_UNIT + " = 'Minutes'", null);
        int total = 0;
        if (cursor.moveToFirst()) total = cursor.getInt(0);
        cursor.close();
        return total;
    }

    public void clearAllMedia() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_MEDIA);
        db.close();
    }
}