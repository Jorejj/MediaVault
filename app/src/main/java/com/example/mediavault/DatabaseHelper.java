package com.example.mediavault;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.example.mediavault.utils.ProgressValueUtils;
import com.example.mediavault.api.MediaMetadataProfile;
import com.example.mediavault.cloud.sync.SupabaseMediaSyncManager;

public class DatabaseHelper extends SQLiteOpenHelper {

    // Database Information
    private static final String DATABASE_NAME = "MediaVault.db";
    private static final int DATABASE_VERSION = 18; // Provider metadata normalization table
    public static final String TABLE_MEDIA = "media_library";
    public static final String TABLE_PROGRESS_LOG = "progress_log";
    public static final String TABLE_DAILY_METRICS = "daily_metrics";
    public static final String TABLE_MEDIA_METADATA = "media_metadata";
    private static final String COLLECTION_PREFS = "library_collection_prefs";
    private static final String KEY_CUSTOM_COLLECTIONS_JSON = "custom_collections_json";

    // Column Constants for daily_metrics
    public static final String COL_DAILY_ID = "id";
    public static final String COL_DAILY_DATE = "date"; // ISO-8601
    public static final String COL_DAILY_CHAPTERS = "chapters_read";
    public static final String COL_DAILY_EPISODES = "episodes_watched";
    public static final String COL_DAILY_MINUTES = "minutes_watched";
    public static final String COL_DAILY_GOAL_MET = "goal_met";

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
    public static final String COL_PREVIOUS_PROGRESS = "previous_progress";
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
    public static final String COL_SOURCE_URL = "source_url";
    public static final String COL_CONTENT_TYPE = "content_type";
    public static final String COL_CURRENT_SEASON = "current_season";
    public static final String COL_CURRENT_EPISODE = "current_episode";
    public static final String COL_METADATA_MEDIA_ID = "media_id";
    public static final String COL_METADATA_CANONICAL_TITLE = "canonical_title";
    public static final String COL_METADATA_NORMALIZED_TITLE = "normalized_title";
    public static final String COL_METADATA_ALT_TITLES_JSON = "alt_titles_json";
    public static final String COL_METADATA_PROVIDER_ID = "provider_id";
    public static final String COL_METADATA_PROVIDER_SLUG = "provider_slug";
    public static final String COL_METADATA_CANONICAL_URL = "canonical_url";
    public static final String COL_METADATA_SOURCE = "metadata_source";
    public static final String COL_METADATA_MEDIA_TYPE = "metadata_media_type";
    public static final String COL_METADATA_SUB_TYPE = "metadata_sub_type";
    public static final String COL_METADATA_LANGUAGE = "metadata_language";
    public static final String COL_METADATA_REGION = "metadata_region";
    public static final String COL_METADATA_STATUS = "metadata_status";
    public static final String COL_METADATA_RELEASE_YEAR = "metadata_release_year";
    public static final String COL_METADATA_TOTAL_COUNT = "metadata_total_count";
    public static final String COL_METADATA_UNIT = "metadata_unit";
    public static final String COL_METADATA_GENRES_JSON = "genres_json";
    public static final String COL_METADATA_TAGS_JSON = "tags_json";
    public static final String COL_METADATA_EXTERNAL_IDS_JSON = "external_ids_json";
    public static final String COL_METADATA_RATING = "metadata_rating";
    public static final String COL_METADATA_POPULARITY = "metadata_popularity";
    public static final String COL_METADATA_PROVIDER_FEATURES_JSON = "provider_features_json";
    public static final String COL_METADATA_CONFIDENCE = "metadata_confidence";
    public static final String COL_METADATA_PRIORITY = "metadata_priority";
    public static final String COL_METADATA_UPDATED_AT = "metadata_updated_at";

    // Column Constants for progress_log
    public static final String COL_LOG_ID = "log_id";
    public static final String COL_LOG_MEDIA_ID = "media_id";
    public static final String COL_PROGRESS_ADDED = "progress_added";
    public static final String COL_LOG_DATE = "log_date";

    private static DatabaseHelper instance;
    private final Context context;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createMediaTable(db);

        // Create progress_log table for metrics
        String createLogTable = "CREATE TABLE " + TABLE_PROGRESS_LOG + " (" +
                COL_LOG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_LOG_MEDIA_ID + " INTEGER NOT NULL, " +
                COL_PROGRESS_ADDED + " REAL NOT NULL, " +
                COL_LOG_DATE + " DATE DEFAULT (date('now', 'localtime')), " +
                "FOREIGN KEY (" + COL_LOG_MEDIA_ID + ") REFERENCES " + TABLE_MEDIA + "(" + COL_ID + ") ON DELETE CASCADE)";
        
        db.execSQL(createLogTable);

        // Create daily_metrics table for streaks
        String createDailyTable = "CREATE TABLE " + TABLE_DAILY_METRICS + " (" +
                COL_DAILY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_DAILY_DATE + " TEXT NOT NULL UNIQUE, " +
                COL_DAILY_CHAPTERS + " INTEGER DEFAULT 0, " +
                COL_DAILY_EPISODES + " INTEGER DEFAULT 0, " +
                COL_DAILY_GOAL_MET + " INTEGER DEFAULT 0)";
        db.execSQL(createDailyTable);

        String createMediaMetadataTable = "CREATE TABLE " + TABLE_MEDIA_METADATA + " (" +
                COL_METADATA_MEDIA_ID + " INTEGER PRIMARY KEY, " +
                COL_METADATA_CANONICAL_TITLE + " TEXT, " +
                COL_METADATA_NORMALIZED_TITLE + " TEXT, " +
                COL_METADATA_ALT_TITLES_JSON + " TEXT, " +
                COL_METADATA_PROVIDER_ID + " TEXT, " +
                COL_METADATA_PROVIDER_SLUG + " TEXT, " +
                COL_METADATA_CANONICAL_URL + " TEXT, " +
                COL_METADATA_SOURCE + " TEXT, " +
                COL_METADATA_MEDIA_TYPE + " TEXT, " +
                COL_METADATA_SUB_TYPE + " TEXT, " +
                COL_METADATA_LANGUAGE + " TEXT, " +
                COL_METADATA_REGION + " TEXT, " +
                COL_METADATA_STATUS + " TEXT, " +
                COL_METADATA_RELEASE_YEAR + " INTEGER, " +
                COL_METADATA_TOTAL_COUNT + " INTEGER, " +
                COL_METADATA_UNIT + " TEXT, " +
                COL_METADATA_GENRES_JSON + " TEXT, " +
                COL_METADATA_TAGS_JSON + " TEXT, " +
                COL_METADATA_EXTERNAL_IDS_JSON + " TEXT, " +
                COL_METADATA_RATING + " REAL, " +
                COL_METADATA_POPULARITY + " REAL, " +
                COL_METADATA_PROVIDER_FEATURES_JSON + " TEXT, " +
                COL_METADATA_CONFIDENCE + " REAL DEFAULT 0.0, " +
                COL_METADATA_PRIORITY + " INTEGER DEFAULT 0, " +
                COL_METADATA_UPDATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (" + COL_METADATA_MEDIA_ID + ") REFERENCES " + TABLE_MEDIA + "(" + COL_ID + ") ON DELETE CASCADE)";
        db.execSQL(createMediaMetadataTable);

        createTrigger(db);
    }

    private void createMediaTable(SQLiteDatabase db) {
        String createMediaTable = "CREATE TABLE " + TABLE_MEDIA + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_API_ID + " TEXT, " +
                COL_TITLE + " TEXT NOT NULL COLLATE NOCASE, " +
                COL_DESCRIPTION + " TEXT, " +
                COL_CREATOR + " TEXT, " +
                COL_MEDIA_TYPE + " TEXT NOT NULL, " +
                COL_GENRE + " TEXT, " +
                COL_IMAGE_PATH + " TEXT, " +
                COL_CURRENT_PROGRESS + " REAL DEFAULT 0.0, " +
                COL_PREVIOUS_PROGRESS + " REAL DEFAULT 0.0, " +
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
                COL_SOURCE_URL + " TEXT, " +
                COL_CONTENT_TYPE + " TEXT, " +
                COL_CURRENT_SEASON + " INTEGER DEFAULT 1, " +
                COL_CURRENT_EPISODE + " INTEGER DEFAULT 1, " +
                "CONSTRAINT unique_title_type UNIQUE (" + COL_TITLE + " COLLATE NOCASE, " + COL_MEDIA_TYPE + "), " +
                "CONSTRAINT check_status CHECK (" + COL_STATUS + " IN ('Ongoing', 'Completed', 'Planning', 'Dropped', 'Recently Deleted')), " +
                "CONSTRAINT check_capacity_unit CHECK (" + COL_UNIT + " IN ('Pages', 'Episodes', 'Minutes', 'Chapters')), " +
                "CONSTRAINT check_user_rating CHECK (" + COL_RATING + " >= 0.0 AND " + COL_RATING + " <= 5.0), " +
                "CONSTRAINT check_priority_level CHECK (" + COL_PRIORITY + " IN ('High', 'Medium', 'Low')), " +
                "CONSTRAINT check_total_capacity CHECK (" + COL_TOTAL_COUNT + " > 0), " +
                "CONSTRAINT check_current_progress CHECK ((" + COL_MEDIA_TYPE + " = 'Series' AND " + COL_CURRENT_PROGRESS + " >= 0) OR (" + COL_MEDIA_TYPE + " != 'Series' AND " + COL_CURRENT_PROGRESS + " >= 0 AND " + COL_CURRENT_PROGRESS + " <= " + COL_TOTAL_COUNT + ")), " +
                "CONSTRAINT check_series_season CHECK (" + COL_CURRENT_SEASON + " >= 1), " +
                "CONSTRAINT check_series_episode CHECK (" + COL_CURRENT_EPISODE + " >= 1), " +
                "CONSTRAINT check_image_path CHECK (" + COL_IMAGE_PATH + " IS NULL OR " + COL_IMAGE_PATH + " != ''))";

        db.execSQL(createMediaTable);
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
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_PROGRESS_LOG);
            String createLogTable = "CREATE TABLE " + TABLE_PROGRESS_LOG + " (" +
                    COL_LOG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_LOG_MEDIA_ID + " INTEGER NOT NULL, " +
                    COL_PROGRESS_ADDED + " REAL NOT NULL, " +
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
        if (oldVersion < 13) {
            db.execSQL("ALTER TABLE " + TABLE_MEDIA + " RENAME TO temp_media");
            createMediaTable(db);
            createTrigger(db);
            
            db.execSQL("INSERT INTO " + TABLE_MEDIA + " (" +
                    COL_ID + ", " + COL_API_ID + ", " + COL_TITLE + ", " + COL_DESCRIPTION + ", " +
                    COL_CREATOR + ", " + COL_MEDIA_TYPE + ", " + COL_GENRE + ", " + COL_IMAGE_PATH + ", " +
                    COL_CURRENT_PROGRESS + ", " + COL_TOTAL_COUNT + ", " + COL_UNIT + ", " + COL_RUNTIME + ", " +
                    COL_STATUS + ", " + COL_RATING + ", " + COL_REVIEW + ", " + COL_JOURNAL + ", " +
                    COL_MOOD + ", " + COL_PRIORITY + ", " + COL_DATE_ADDED + ", " + COL_LAST_UPDATED + ", " +
                    COL_IS_FAVORITE + ") " +
                    "SELECT " +
                    COL_ID + ", " + COL_API_ID + ", " + COL_TITLE + ", " + COL_DESCRIPTION + ", " +
                    COL_CREATOR + ", " + COL_MEDIA_TYPE + ", " + COL_GENRE + ", " + COL_IMAGE_PATH + ", " +
                    "CAST(" + COL_CURRENT_PROGRESS + " AS REAL), " +
                    COL_TOTAL_COUNT + ", " + COL_UNIT + ", " + COL_RUNTIME + ", " +
                    COL_STATUS + ", " + COL_RATING + ", " + COL_REVIEW + ", " + COL_JOURNAL + ", " +
                    COL_MOOD + ", " + COL_PRIORITY + ", " + COL_DATE_ADDED + ", " + COL_LAST_UPDATED + ", " +
                    COL_IS_FAVORITE + " FROM temp_media");
            
            db.execSQL("DROP TABLE temp_media");
        }
        if (oldVersion < 14) {
            // Safe 4-step migration for Version 14
            db.execSQL("ALTER TABLE " + TABLE_MEDIA + " RENAME TO temp_media");
            
            // Re-create tables as defined in current onCreate with ALL constraints
            db.execSQL("CREATE TABLE " + TABLE_MEDIA + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_API_ID + " TEXT, " +
                    COL_TITLE + " TEXT NOT NULL COLLATE NOCASE, " +
                    COL_DESCRIPTION + " TEXT, " +
                    COL_CREATOR + " TEXT, " +
                    COL_MEDIA_TYPE + " TEXT NOT NULL, " +
                    COL_GENRE + " TEXT, " +
                    COL_IMAGE_PATH + " TEXT, " +
                    COL_CURRENT_PROGRESS + " REAL DEFAULT 0.0, " +
                    COL_PREVIOUS_PROGRESS + " REAL DEFAULT 0.0, " +
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
                    COL_SOURCE_URL + " TEXT, " +
                    COL_CONTENT_TYPE + " TEXT, " +
                    "CONSTRAINT unique_title UNIQUE (" + COL_TITLE + " COLLATE NOCASE), " +
                    "CONSTRAINT check_status CHECK (" + COL_STATUS + " IN ('Ongoing', 'Completed', 'Planning', 'Dropped', 'Recently Deleted')), " +
                    "CONSTRAINT check_capacity_unit CHECK (" + COL_UNIT + " IN ('Pages', 'Episodes', 'Minutes', 'Chapters')), " +
                    "CONSTRAINT check_user_rating CHECK (" + COL_RATING + " >= 0.0 AND " + COL_RATING + " <= 5.0), " +
                    "CONSTRAINT check_priority_level CHECK (" + COL_PRIORITY + " IN ('High', 'Medium', 'Low')), " +
                    "CONSTRAINT check_total_capacity CHECK (" + COL_TOTAL_COUNT + " > 0), " +
                    "CONSTRAINT check_current_progress CHECK (" + COL_CURRENT_PROGRESS + " >= 0 AND " + COL_CURRENT_PROGRESS + " <= 99999))"); // ISSUE #2 FIX: Allow encoded TV progress (season*1000+episode)

            // Migration logic: v13 does not have source_url or content_type
            db.execSQL("INSERT INTO " + TABLE_MEDIA + " (" +
                    COL_ID + ", " + COL_API_ID + ", " + COL_TITLE + ", " + COL_DESCRIPTION + ", " +
                    COL_CREATOR + ", " + COL_MEDIA_TYPE + ", " + COL_GENRE + ", " + COL_IMAGE_PATH + ", " +
                    COL_CURRENT_PROGRESS + ", " + COL_PREVIOUS_PROGRESS + ", " + COL_TOTAL_COUNT + ", " + COL_UNIT + ", " +
                    COL_RUNTIME + ", " + COL_STATUS + ", " + COL_RATING + ", " + COL_REVIEW + ", " +
                    COL_JOURNAL + ", " + COL_MOOD + ", " + COL_PRIORITY + ", " + COL_DATE_ADDED + ", " +
                    COL_LAST_UPDATED + ", " + COL_IS_FAVORITE + ", " + COL_SOURCE_URL + ", " + COL_CONTENT_TYPE + ") " +
                    "SELECT " +
                    COL_ID + ", " + COL_API_ID + ", " + COL_TITLE + ", " + COL_DESCRIPTION + ", " +
                    COL_CREATOR + ", " + COL_MEDIA_TYPE + ", " + COL_GENRE + ", " + COL_IMAGE_PATH + ", " +
                    COL_CURRENT_PROGRESS + ", " + COL_CURRENT_PROGRESS + ", " + COL_TOTAL_COUNT + ", " + COL_UNIT + ", " +
                    COL_RUNTIME + ", " + COL_STATUS + ", " + COL_RATING + ", " + COL_REVIEW + ", " +
                    COL_JOURNAL + ", " + COL_MOOD + ", " + COL_PRIORITY + ", " + COL_DATE_ADDED + ", " +
                    COL_LAST_UPDATED + ", " + COL_IS_FAVORITE + ", NULL, NULL FROM temp_media");

            db.execSQL("DROP TABLE temp_media");
        }
        
        if (oldVersion < 15) {
            // Explicitly create daily_metrics if it doesn't exist (Safe for all paths)
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_DAILY_METRICS + " (" +
                    COL_DAILY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_DAILY_DATE + " TEXT NOT NULL UNIQUE, " +
                    COL_DAILY_CHAPTERS + " INTEGER DEFAULT 0, " +
                    COL_DAILY_EPISODES + " INTEGER DEFAULT 0, " +
                    COL_DAILY_GOAL_MET + " INTEGER DEFAULT 0)");
        }
        if (oldVersion < 17) {
            db.execSQL("ALTER TABLE " + TABLE_MEDIA + " RENAME TO temp_media_v17");
            db.execSQL("CREATE TABLE " + TABLE_MEDIA + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_API_ID + " TEXT, " +
                    COL_TITLE + " TEXT NOT NULL COLLATE NOCASE, " +
                    COL_DESCRIPTION + " TEXT, " +
                    COL_CREATOR + " TEXT, " +
                    COL_MEDIA_TYPE + " TEXT NOT NULL, " +
                    COL_GENRE + " TEXT, " +
                    COL_IMAGE_PATH + " TEXT, " +
                    COL_CURRENT_PROGRESS + " REAL DEFAULT 0.0, " +
                    COL_PREVIOUS_PROGRESS + " REAL DEFAULT 0.0, " +
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
                    COL_SOURCE_URL + " TEXT, " +
                    COL_CONTENT_TYPE + " TEXT, " +
                    COL_CURRENT_SEASON + " INTEGER DEFAULT 1, " +
                    COL_CURRENT_EPISODE + " INTEGER DEFAULT 1, " +
                    "CONSTRAINT unique_title_type UNIQUE (" + COL_TITLE + " COLLATE NOCASE, " + COL_MEDIA_TYPE + "), " +
                    "CONSTRAINT check_status CHECK (" + COL_STATUS + " IN ('Ongoing', 'Completed', 'Planning', 'Dropped', 'Recently Deleted')), " +
                    "CONSTRAINT check_capacity_unit CHECK (" + COL_UNIT + " IN ('Pages', 'Episodes', 'Minutes', 'Chapters')), " +
                    "CONSTRAINT check_user_rating CHECK (" + COL_RATING + " >= 0.0 AND " + COL_RATING + " <= 5.0), " +
                    "CONSTRAINT check_priority_level CHECK (" + COL_PRIORITY + " IN ('High', 'Medium', 'Low')), " +
                    "CONSTRAINT check_total_capacity CHECK (" + COL_TOTAL_COUNT + " > 0), " +
                    "CONSTRAINT check_current_progress CHECK ((" + COL_MEDIA_TYPE + " = 'Series' AND " + COL_CURRENT_PROGRESS + " >= 0) OR (" + COL_MEDIA_TYPE + " != 'Series' AND " + COL_CURRENT_PROGRESS + " >= 0 AND " + COL_CURRENT_PROGRESS + " <= " + COL_TOTAL_COUNT + ")), " +
                    "CONSTRAINT check_series_season CHECK (" + COL_CURRENT_SEASON + " >= 1), " +
                    "CONSTRAINT check_series_episode CHECK (" + COL_CURRENT_EPISODE + " >= 1), " +
                    "CONSTRAINT check_image_path CHECK (" + COL_IMAGE_PATH + " IS NULL OR " + COL_IMAGE_PATH + " != ''))");

            db.execSQL("INSERT INTO " + TABLE_MEDIA + " (" +
                    COL_ID + ", " + COL_API_ID + ", " + COL_TITLE + ", " + COL_DESCRIPTION + ", " +
                    COL_CREATOR + ", " + COL_MEDIA_TYPE + ", " + COL_GENRE + ", " + COL_IMAGE_PATH + ", " +
                    COL_CURRENT_PROGRESS + ", " + COL_PREVIOUS_PROGRESS + ", " + COL_TOTAL_COUNT + ", " + COL_UNIT + ", " +
                    COL_RUNTIME + ", " + COL_STATUS + ", " + COL_RATING + ", " + COL_REVIEW + ", " +
                    COL_JOURNAL + ", " + COL_MOOD + ", " + COL_PRIORITY + ", " + COL_DATE_ADDED + ", " +
                    COL_LAST_UPDATED + ", " + COL_IS_FAVORITE + ", " + COL_SOURCE_URL + ", " + COL_CONTENT_TYPE + ", " +
                    COL_CURRENT_SEASON + ", " + COL_CURRENT_EPISODE + ") " +
                    "SELECT " +
                    COL_ID + ", " + COL_API_ID + ", " + COL_TITLE + ", " + COL_DESCRIPTION + ", " +
                    COL_CREATOR + ", " + COL_MEDIA_TYPE + ", " + COL_GENRE + ", " + COL_IMAGE_PATH + ", " +
                    "CASE WHEN " + COL_MEDIA_TYPE + " = 'Series' AND " + COL_CURRENT_PROGRESS + " >= 1000 THEN CAST(CAST(" + COL_CURRENT_PROGRESS + " AS INTEGER) % 1000 AS REAL) ELSE " + COL_CURRENT_PROGRESS + " END, " +
                    "CASE WHEN " + COL_MEDIA_TYPE + " = 'Series' AND " + COL_PREVIOUS_PROGRESS + " >= 1000 THEN CAST(CAST(" + COL_PREVIOUS_PROGRESS + " AS INTEGER) % 1000 AS REAL) ELSE " + COL_PREVIOUS_PROGRESS + " END, " +
                    COL_TOTAL_COUNT + ", " + COL_UNIT + ", " +
                    COL_RUNTIME + ", " + COL_STATUS + ", " + COL_RATING + ", " + COL_REVIEW + ", " +
                    COL_JOURNAL + ", " + COL_MOOD + ", " + COL_PRIORITY + ", " + COL_DATE_ADDED + ", " +
                    COL_LAST_UPDATED + ", " + COL_IS_FAVORITE + ", " + COL_SOURCE_URL + ", " + COL_CONTENT_TYPE + ", " +
                    "CASE WHEN " + COL_MEDIA_TYPE + " = 'Series' AND " + COL_CURRENT_PROGRESS + " >= 1000 THEN CAST(" + COL_CURRENT_PROGRESS + " / 1000 AS INTEGER) WHEN " + COL_MEDIA_TYPE + " = 'Series' THEN 1 ELSE 1 END, " +
                    "CASE WHEN " + COL_MEDIA_TYPE + " = 'Series' AND " + COL_CURRENT_PROGRESS + " >= 1000 THEN CAST(CAST(" + COL_CURRENT_PROGRESS + " AS INTEGER) % 1000 AS INTEGER) WHEN " + COL_MEDIA_TYPE + " = 'Series' AND " + COL_CURRENT_PROGRESS + " >= 1 THEN CAST(" + COL_CURRENT_PROGRESS + " AS INTEGER) ELSE 1 END " +
                    "FROM temp_media_v17");

            db.execSQL("DROP TABLE temp_media_v17");
        }
        if (oldVersion < 18) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_MEDIA_METADATA + " (" +
                    COL_METADATA_MEDIA_ID + " INTEGER PRIMARY KEY, " +
                    COL_METADATA_CANONICAL_TITLE + " TEXT, " +
                    COL_METADATA_NORMALIZED_TITLE + " TEXT, " +
                    COL_METADATA_ALT_TITLES_JSON + " TEXT, " +
                    COL_METADATA_PROVIDER_ID + " TEXT, " +
                    COL_METADATA_PROVIDER_SLUG + " TEXT, " +
                    COL_METADATA_CANONICAL_URL + " TEXT, " +
                    COL_METADATA_SOURCE + " TEXT, " +
                    COL_METADATA_MEDIA_TYPE + " TEXT, " +
                    COL_METADATA_SUB_TYPE + " TEXT, " +
                    COL_METADATA_LANGUAGE + " TEXT, " +
                    COL_METADATA_REGION + " TEXT, " +
                    COL_METADATA_STATUS + " TEXT, " +
                    COL_METADATA_RELEASE_YEAR + " INTEGER, " +
                    COL_METADATA_TOTAL_COUNT + " INTEGER, " +
                    COL_METADATA_UNIT + " TEXT, " +
                    COL_METADATA_GENRES_JSON + " TEXT, " +
                    COL_METADATA_TAGS_JSON + " TEXT, " +
                    COL_METADATA_EXTERNAL_IDS_JSON + " TEXT, " +
                    COL_METADATA_RATING + " REAL, " +
                    COL_METADATA_POPULARITY + " REAL, " +
                    COL_METADATA_PROVIDER_FEATURES_JSON + " TEXT, " +
                    COL_METADATA_CONFIDENCE + " REAL DEFAULT 0.0, " +
                    COL_METADATA_PRIORITY + " INTEGER DEFAULT 0, " +
                    COL_METADATA_UPDATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (" + COL_METADATA_MEDIA_ID + ") REFERENCES " + TABLE_MEDIA + "(" + COL_ID + ") ON DELETE CASCADE)");
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
        return addMedia(title, type, genre, creator, totalCount, unit, runtime, imagePath, description, null, null);
    }

    public long addMedia(String title, String type, String genre, String creator, int totalCount, String unit, String runtime, String imagePath, String description, String sourceUrl, String contentType) {
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
        values.put(COL_SOURCE_URL, sourceUrl);
        values.put(COL_CONTENT_TYPE, contentType);
        values.put(COL_CURRENT_SEASON, 1);
        values.put(COL_CURRENT_EPISODE, 1);
        long result = db.insertWithOnConflict(TABLE_MEDIA, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        db.close();
        if (result > 0 && result <= Integer.MAX_VALUE) {
            SupabaseMediaSyncManager.enqueueUpsertMedia(context, (int) result);
            SupabaseMediaSyncManager.enqueueUserEvent(
                    context,
                    "open",
                    1.0,
                    "add_media:local_id=" + result
            );
        }
        return result;
    }

    public Cursor getAllMedia() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_MEDIA + " WHERE " + COL_STATUS + " != 'Recently Deleted' ORDER BY " + COL_LAST_UPDATED + " DESC", null);
    }

    public Cursor getAllMediaWithMetadata() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT m.*, " +
                "mm." + COL_METADATA_CANONICAL_TITLE + " AS metadata_canonical_title, " +
                "mm." + COL_METADATA_NORMALIZED_TITLE + " AS metadata_normalized_title, " +
                "mm." + COL_METADATA_ALT_TITLES_JSON + " AS metadata_alt_titles_json, " +
                "mm." + COL_METADATA_EXTERNAL_IDS_JSON + " AS metadata_external_ids_json " +
                "FROM " + TABLE_MEDIA + " m " +
                "LEFT JOIN " + TABLE_MEDIA_METADATA + " mm ON m." + COL_ID + " = mm." + COL_METADATA_MEDIA_ID + " " +
                "WHERE m." + COL_STATUS + " != 'Recently Deleted' " +
                "ORDER BY m." + COL_LAST_UPDATED + " DESC";
        return db.rawQuery(query, null);
    }

    public Cursor getAllMediaIncludingTrash() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_MEDIA + " ORDER BY " + COL_LAST_UPDATED + " DESC", null);
    }

    public Cursor getMediaById(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_MEDIA + " WHERE " + COL_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public Cursor getMediaByTitle(String title) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_MEDIA + " WHERE " + COL_TITLE + " = ? COLLATE NOCASE", new String[]{title});
    }

    public List<com.example.mediavault.ui.library.MediaItem> getContentForSeries(int seriesId) {
        List<com.example.mediavault.ui.library.MediaItem> items = new ArrayList<>();
        try (Cursor cursor = getMediaById(seriesId)) {
            if (cursor != null && cursor.moveToFirst()) {
                items.add(new com.example.mediavault.ui.library.MediaItem(
                    cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_MEDIA_TYPE)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_GENRE)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS)),
                    cursor.getFloat(cursor.getColumnIndexOrThrow(COL_CURRENT_PROGRESS)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(COL_TOTAL_COUNT)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_UNIT)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_IMAGE_PATH)),
                    cursor.getFloat(cursor.getColumnIndexOrThrow(COL_RATING))
                ));
            }
        }
        return items;
    }

    public float getMediaRating(int id) {
        float rating = 0f;
        try (Cursor cursor = getMediaById(id)) {
            if (cursor != null && cursor.moveToFirst()) {
                rating = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_RATING));
            }
        }
        return rating;
    }

    public boolean updateMedia(int id, String title, String type, String genre, String status, float progress, int total, String unit, String imagePath, float rating, String review, String description, String creator) {
        SQLiteDatabase db = this.getWritableDatabase();
        float oldProgress = getCurrentProgress(db, id);
        float normalizedProgress = ProgressValueUtils.normalizeForUnit(progress, unit);
        if (total > 0) {
            normalizedProgress = Math.min(normalizedProgress, total);
        }
        ContentValues values = new ContentValues();
        values.put(COL_TITLE, title);
        values.put(COL_MEDIA_TYPE, type);
        values.put(COL_GENRE, genre);
        values.put(COL_STATUS, status);
        values.put(COL_PREVIOUS_PROGRESS, oldProgress);
        values.put(COL_CURRENT_PROGRESS, normalizedProgress);
        values.put(COL_TOTAL_COUNT, total);
        values.put(COL_UNIT, unit);
        values.put(COL_IMAGE_PATH, imagePath);
        values.put(COL_RATING, rating);
        values.put(COL_REVIEW, review);
        values.put(COL_DESCRIPTION, description);
        values.put(COL_CREATOR, creator);
        if (normalizedProgress > oldProgress) {
            logProgressDelta(db, id, normalizedProgress - oldProgress);
        }
        int result = db.update(TABLE_MEDIA, values, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
        if (result > 0) {
            SupabaseMediaSyncManager.enqueueUpsertMedia(context, id);
        }
        return result > 0;
    }

    public boolean updateMedia(int id, String title, String type, String genre, String status, float progress, int total, String unit, String imagePath, float rating, String review, String journal, String mood, String priority, boolean isFavorite, String description, String creator) {
        SQLiteDatabase db = this.getWritableDatabase();
        float oldProgress = getCurrentProgress(db, id);
        float normalizedProgress = ProgressValueUtils.normalizeForUnit(progress, unit);
        if (total > 0) {
            normalizedProgress = Math.min(normalizedProgress, total);
        }
        ContentValues values = new ContentValues();
        values.put(COL_TITLE, title);
        values.put(COL_MEDIA_TYPE, type);
        values.put(COL_GENRE, genre);
        values.put(COL_STATUS, status);
        values.put(COL_PREVIOUS_PROGRESS, oldProgress);
        values.put(COL_CURRENT_PROGRESS, normalizedProgress);
        values.put(COL_TOTAL_COUNT, total);
        values.put(COL_UNIT, unit);
        values.put(COL_IMAGE_PATH, imagePath);
        values.put(COL_RATING, rating);
        values.put(COL_REVIEW, review);
        values.put(COL_JOURNAL, journal);
        values.put(COL_MOOD, mood);
        values.put(COL_PRIORITY, priority == null || priority.isEmpty() ? "Medium" : priority);
        values.put(COL_IS_FAVORITE, isFavorite ? 1 : 0);
        values.put(COL_DESCRIPTION, description);
        values.put(COL_CREATOR, creator);
        if (normalizedProgress > oldProgress) {
            logProgressDelta(db, id, normalizedProgress - oldProgress);
        }
        int result = db.update(TABLE_MEDIA, values, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
        if (result > 0) {
            SupabaseMediaSyncManager.enqueueUpsertMedia(context, id);
        }
        return result > 0;
    }

    public boolean updateProgress(int id, float newProgress, String newStatus, float newRating) {
        SQLiteDatabase db = this.getWritableDatabase();
        
        // ISSUE #13 FIX: Preserve existing values when null/0 passed
        float oldProgress = 0;
        String currentStatus = null;
        float currentRating = 0;
        String currentUnit = null;
        int totalCount = 0;
        
        Cursor cursor = db.query(TABLE_MEDIA, 
                new String[]{COL_CURRENT_PROGRESS, COL_STATUS, COL_RATING, COL_UNIT, COL_TOTAL_COUNT}, 
                COL_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                oldProgress = cursor.getFloat(0);
                currentStatus = cursor.getString(1);
                currentRating = cursor.getFloat(2);
                currentUnit = cursor.getString(3);
                totalCount = cursor.getInt(4);
            }
            cursor.close();
        }

        float normalizedProgress = ProgressValueUtils.normalizeForUnit(newProgress, currentUnit);
        if (totalCount > 0) {
            normalizedProgress = Math.min(normalizedProgress, totalCount);
        }

        float progressAdded = normalizedProgress - oldProgress;

        if (progressAdded > 0) {
            logProgressDelta(db, id, progressAdded);
        }

        ContentValues values = new ContentValues();
        values.put(COL_PREVIOUS_PROGRESS, oldProgress);
        values.put(COL_CURRENT_PROGRESS, normalizedProgress);
        // Preserve existing values if null/0 passed
        values.put(COL_STATUS, newStatus != null ? newStatus : currentStatus);
        values.put(COL_RATING, newRating > 0 ? newRating : currentRating);
        values.put(COL_LAST_UPDATED, getDateTime());
        int result = db.update(TABLE_MEDIA, values, COL_ID + "=?", new String[]{String.valueOf(id)});
        
        db.close();
        if (result > 0) {
            SupabaseMediaSyncManager.enqueueUpsertMedia(context, id);
            SupabaseMediaSyncManager.enqueueUserEvent(
                    context,
                    "progress",
                    Math.max(0f, progressAdded),
                    "progress_update:local_id=" + id
            );
        }
        return result > 0;
    }

    public boolean updateSourceAndContent(int id, String sourceUrl, String contentType) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_SOURCE_URL, sourceUrl);
        values.put(COL_CONTENT_TYPE, contentType);
        values.put(COL_LAST_UPDATED, getDateTime());
        int result = db.update(TABLE_MEDIA, values, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
        if (result > 0) {
            SupabaseMediaSyncManager.enqueueUpsertMedia(context, id);
        }
        return result > 0;
    }

    public boolean updateSeriesProgress(int id, int season, int episode, String newStatus, float newRating) {
        SQLiteDatabase db = this.getWritableDatabase();
        float oldProgress = 0f;
        String currentStatus = null;
        float currentRating = 0f;
        int totalCount = Integer.MAX_VALUE;

        Cursor cursor = db.query(
                TABLE_MEDIA,
                new String[]{COL_CURRENT_PROGRESS, COL_STATUS, COL_RATING, COL_TOTAL_COUNT},
                COL_ID + "=?",
                new String[]{String.valueOf(id)},
                null,
                null,
                null
        );
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                oldProgress = cursor.getFloat(0);
                currentStatus = cursor.getString(1);
                currentRating = cursor.getFloat(2);
                totalCount = cursor.getInt(3);
            }
            cursor.close();
        }

        int safeSeason = Math.max(1, season);
        int safeEpisode = Math.max(1, episode);
        float normalizedProgress = Math.max(oldProgress, (float) safeEpisode);
        if (totalCount > 0) {
            normalizedProgress = Math.min(normalizedProgress, totalCount);
        }
        float progressAdded = normalizedProgress - oldProgress;
        if (progressAdded > 0f) {
            logProgressDelta(db, id, progressAdded);
        }
        String resolvedStatus = newStatus;
        if (resolvedStatus == null) {
            resolvedStatus = currentStatus;
        }
        if (totalCount > 0 && normalizedProgress >= totalCount) {
            resolvedStatus = "Completed";
        }

        ContentValues values = new ContentValues();
        values.put(COL_PREVIOUS_PROGRESS, oldProgress);
        values.put(COL_CURRENT_PROGRESS, normalizedProgress);
        values.put(COL_CURRENT_SEASON, safeSeason);
        values.put(COL_CURRENT_EPISODE, safeEpisode);
        values.put(COL_STATUS, resolvedStatus);
        values.put(COL_RATING, newRating > 0 ? newRating : currentRating);
        values.put(COL_LAST_UPDATED, getDateTime());
        int result = db.update(TABLE_MEDIA, values, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
        if (result > 0) {
            SupabaseMediaSyncManager.enqueueUpsertMedia(context, id);
            SupabaseMediaSyncManager.enqueueUserEvent(
                    context,
                    "progress",
                    Math.max(0f, progressAdded),
                    "series_progress:local_id=" + id + ":s" + safeSeason + "e" + safeEpisode
            );
        }
        return result > 0;
    }

    public boolean updateProgressStatusMood(int id, float newProgress, String newStatus, float newRating, String mood) {
        SQLiteDatabase db = this.getWritableDatabase();

        float oldProgress = 0;
        String currentUnit = null;
        int totalCount = 0;
        Cursor cursor = db.query(TABLE_MEDIA, new String[]{COL_CURRENT_PROGRESS, COL_UNIT, COL_TOTAL_COUNT},
                COL_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                oldProgress = cursor.getFloat(0);
                currentUnit = cursor.getString(1);
                totalCount = cursor.getInt(2);
            }
            cursor.close();
        }

        float normalizedProgress = ProgressValueUtils.normalizeForUnit(newProgress, currentUnit);
        if (totalCount > 0) {
            normalizedProgress = Math.min(normalizedProgress, totalCount);
        }

        float progressAdded = normalizedProgress - oldProgress;
        if (progressAdded > 0) {
            logProgressDelta(db, id, progressAdded);
        }

        ContentValues values = new ContentValues();
        values.put(COL_PREVIOUS_PROGRESS, oldProgress);
        values.put(COL_CURRENT_PROGRESS, normalizedProgress);
        values.put(COL_STATUS, newStatus);
        values.put(COL_RATING, newRating);
        values.put(COL_MOOD, mood);
        values.put(COL_LAST_UPDATED, getDateTime());
        int result = db.update(TABLE_MEDIA, values, COL_ID + "=?", new String[]{String.valueOf(id)});

        db.close();
        if (result > 0) {
            SupabaseMediaSyncManager.enqueueUpsertMedia(context, id);
            SupabaseMediaSyncManager.enqueueUserEvent(
                    context,
                    "progress",
                    Math.max(0f, progressAdded),
                    "progress_status_mood:local_id=" + id
            );
        }
        return result > 0;
    }

    public boolean deleteMedia(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_MEDIA, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
        if (result > 0) {
            SupabaseMediaSyncManager.enqueueDeleteMedia(context, id);
        }
        return result > 0;
    }

    public boolean updateImagePath(int id, String newPath) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_IMAGE_PATH, newPath);
        int result = db.update(TABLE_MEDIA, values, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
        if (result > 0) {
            SupabaseMediaSyncManager.enqueueUpsertMedia(context, id);
        }
        return result > 0;
    }

    // --- DASHBOARD METRICS QUERIES ---

    public float getDailyPages() {
        return getProgressSumWithFallback(new String[]{"Pages", "Chapters"}, "date('now', 'localtime')");
    }

    public float getWeeklyPages() {
        return getProgressSumWithFallback(new String[]{"Pages", "Chapters"}, "date('now', 'localtime', '-7 days')");
    }

    public float getMonthlyPages() {
        return getProgressSumWithFallback(new String[]{"Pages", "Chapters"}, "date('now', 'localtime', '-30 days')");
    }

    public float getDailyMinutes() {
        return getProgressSumWithFallback(new String[]{"Minutes"}, "date('now', 'localtime')");
    }

    public float getWeeklyMinutes() {
        return getProgressSumWithFallback(new String[]{"Minutes"}, "date('now', 'localtime', '-7 days')");
    }

    public float getDailyEpisodes() {
        return getProgressSumWithFallback(new String[]{"Episodes"}, "date('now', 'localtime')");
    }

    public float getWeeklyEpisodes() {
        return getProgressSumWithFallback(new String[]{"Episodes"}, "date('now', 'localtime', '-7 days')");
    }

    public float getMonthlyMinutes() {
        return getProgressSumWithFallback(new String[]{"Minutes"}, "date('now', 'localtime', '-30 days')");
    }

    public float getMonthlyEpisodes() {
        return getProgressSumWithFallback(new String[]{"Episodes"}, "date('now', 'localtime', '-30 days')");
    }

    private float getProgressSumWithFallback(String[] units, String dateFilter) {
        SQLiteDatabase db = this.getReadableDatabase();
        String inClause = buildInClause(units.length);
        String logQuery = "SELECT COALESCE(SUM(l." + COL_PROGRESS_ADDED + "), 0) FROM " + TABLE_PROGRESS_LOG + " l " +
                "JOIN " + TABLE_MEDIA + " m ON l." + COL_LOG_MEDIA_ID + " = m." + COL_ID + " " +
                "WHERE m." + COL_UNIT + " IN (" + inClause + ") " +
                "AND l." + COL_LOG_DATE + " >= " + dateFilter + " " +
                "AND m." + COL_STATUS + " != 'Recently Deleted'";

        Cursor logCursor = db.rawQuery(logQuery, units);
        float totalFromLog = 0;
        if (logCursor.moveToFirst()) {
            totalFromLog = logCursor.getFloat(0);
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
        float totalFallback = 0;
        if (fallbackCursor.moveToFirst()) {
            totalFallback = fallbackCursor.getFloat(0);
        }
        fallbackCursor.close();
        return totalFallback;
    }

    public float getTotalMinutesWatched() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT " +
                "SUM(CASE " +
                "  WHEN " + COL_UNIT + " = 'Minutes' THEN " + COL_CURRENT_PROGRESS + " " +
                "  WHEN " + COL_UNIT + " = 'Episodes' THEN " + COL_CURRENT_PROGRESS + " * 24 " +
                "  WHEN (" + COL_MEDIA_TYPE + " = 'Movie' OR " + COL_MEDIA_TYPE + " = 'Series') AND " + COL_UNIT + " NOT IN ('Minutes', 'Episodes') THEN " + COL_CURRENT_PROGRESS + " * 120 " +
                "  ELSE 0 END) " +
                "FROM " + TABLE_MEDIA + " WHERE " + COL_STATUS + " != 'Recently Deleted'";
        
        float total = 0;
        try (Cursor cursor = db.rawQuery(query, null);) {
            if (cursor != null && cursor.moveToFirst()) total = cursor.getFloat(0);
        }
        return total;
    }

    public float getTotalPagesRead() {
        SQLiteDatabase db = this.getReadableDatabase();
        float total = 0;
        try (Cursor cursor = db.rawQuery("SELECT SUM(" + COL_CURRENT_PROGRESS + ") FROM " + TABLE_MEDIA + " WHERE " + COL_UNIT + " IN ('Pages', 'Chapters') AND " + COL_STATUS + " != 'Recently Deleted'", null);) {
            if (cursor != null && cursor.moveToFirst()) total = cursor.getFloat(0);
        }
        return total;
    }

    public float getTotalEpisodesWatched() {
        SQLiteDatabase db = this.getReadableDatabase();
        float total = 0;
        try (Cursor cursor = db.rawQuery("SELECT SUM(" + COL_CURRENT_PROGRESS + ") FROM " + TABLE_MEDIA + " WHERE " + COL_UNIT + " = 'Episodes' AND " + COL_STATUS + " != 'Recently Deleted'", null);) {
            if (cursor != null && cursor.moveToFirst()) total = cursor.getFloat(0);
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
        Set<String> collectionTags = getCustomCollectionNamesLowercase();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + COL_GENRE + " FROM " + TABLE_MEDIA + " WHERE " + COL_STATUS + " != 'Recently Deleted'", null);
        if (cursor.moveToFirst()) {
            do {
                String rawGenre = cursor.getString(0);
                addGenreTokens(counts, rawGenre, collectionTags);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return counts;
    }

    private void addGenreTokens(java.util.Map<String, Integer> counts, String rawGenre, Set<String> collectionTags) {
        if (rawGenre == null || rawGenre.trim().isEmpty()) {
            counts.put("Uncategorized", counts.getOrDefault("Uncategorized", 0) + 1);
            return;
        }
        boolean addedAnyGenre = false;
        String[] genres = rawGenre.split(",");
        for (String token : genres) {
            String normalized = token.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            if (collectionTags.contains(normalized.toLowerCase(Locale.ROOT))) {
                continue;
            }
            counts.put(normalized, counts.getOrDefault(normalized, 0) + 1);
            addedAnyGenre = true;
        }
        if (!addedAnyGenre) {
            counts.put("Uncategorized", counts.getOrDefault("Uncategorized", 0) + 1);
        }
    }

    private Set<String> getCustomCollectionNamesLowercase() {
        Set<String> names = new HashSet<>();
        SharedPreferences prefs = context.getSharedPreferences(COLLECTION_PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_CUSTOM_COLLECTIONS_JSON, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.optJSONObject(i);
                if (entry == null) {
                    continue;
                }
                String name = entry.optString("name", "").trim();
                if (!name.isEmpty()) {
                    names.add(name.toLowerCase(Locale.ROOT));
                }
            }
        } catch (JSONException ignored) {
        }
        return names;
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
        if (result > 0) {
            SupabaseMediaSyncManager.enqueueUpsertMedia(context, id);
            SupabaseMediaSyncManager.enqueueUserEvent(
                    context,
                    isFavorite ? "favorite" : "unfavorite",
                    1.0,
                    "metadata_toggle:local_id=" + id
            );
        }
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

        float current = cursor.getFloat(0);
        int total = cursor.getInt(1);
        String status = cursor.getString(2);
        float rating = cursor.getFloat(3);
        cursor.close();

        float newProgress = Math.min(current + 1.0f, (float) total);
        String newStatus = newProgress >= (float) total ? "Completed" : status;
        ContentValues values = new ContentValues();
        values.put(COL_PREVIOUS_PROGRESS, current);
        values.put(COL_CURRENT_PROGRESS, newProgress);
        values.put(COL_STATUS, newStatus);
        values.put(COL_RATING, rating);
        int updatedRows = db.update(TABLE_MEDIA, values, COL_ID + "=?", new String[]{String.valueOf(mediaId)});
        db.close();
        if (updatedRows > 0) {
            SupabaseMediaSyncManager.enqueueUpsertMedia(context, mediaId);
            SupabaseMediaSyncManager.enqueueUserEvent(
                    context,
                    "progress",
                    1.0,
                    "quick_plus:local_id=" + mediaId
            );
        }
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

    public float getDailyProgress(String unit, String datePattern) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT SUM(p." + COL_PROGRESS_ADDED + ") FROM " + TABLE_PROGRESS_LOG + " p " +
                "JOIN " + TABLE_MEDIA + " m ON p." + COL_LOG_MEDIA_ID + " = m." + COL_ID + " " +
                "WHERE m." + COL_UNIT + " = ? AND p." + COL_LOG_DATE + " LIKE ?";
        
        Cursor cursor = db.rawQuery(query, new String[]{unit, datePattern + "%"});
        float total = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                total = cursor.getFloat(0);
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
            float progress;
            if ("Completed".equals(status)) {
                progress = (float) total;
            } else if ("Planning".equals(status)) {
                progress = 0f;
            } else if ("Dropped".equals(status)) {
                progress = (float) Math.max(1, total / 10);
            } else {
                progress = (float) Math.max(1, (int) (total * 0.45f));
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

            v.put(COL_TITLE, title);
            v.put(COL_MEDIA_TYPE, type);
            v.put(COL_GENRE, genre);
            v.put(COL_CREATOR, creator);
            v.put(COL_TOTAL_COUNT, total);
            v.put(COL_UNIT, unit);
            v.put(COL_STATUS, status);
            v.put(COL_CURRENT_PROGRESS, progress);
            v.put(COL_PREVIOUS_PROGRESS, progress);
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
        db.execSQL("DELETE FROM " + TABLE_MEDIA_METADATA);
        db.execSQL("DELETE FROM " + TABLE_MEDIA);
        db.close();
    }

    public boolean upsertMediaMetadata(int mediaId, MediaMetadataProfile profile) {
        return mergeAndUpsertMetadata(mediaId, profile, profile != null ? profile.getMetadataSource() : null);
    }

    public boolean mergeAndUpsertMetadata(int mediaId, MediaMetadataProfile incomingProfile, String sourceType) {
        if (mediaId <= 0 || incomingProfile == null) {
            return false;
        }

        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT * FROM " + TABLE_MEDIA_METADATA + " WHERE " + COL_METADATA_MEDIA_ID + "=?",
                    new String[]{String.valueOf(mediaId)}
            );
            MediaMetadataProfile existing = null;
            if (cursor.moveToFirst()) {
                existing = readMetadataProfile(cursor);
            }

            String resolvedIncomingSource = normalizeMetadataSource(sourceType, incomingProfile.getMetadataSource());
            incomingProfile.withMetadataSource(resolvedIncomingSource).stampNow();

            MediaMetadataProfile merged = (existing == null)
                    ? incomingProfile
                    : mergeMetadataProfiles(existing, incomingProfile, resolvedIncomingSource);

            ContentValues values = toMetadataContentValues(mediaId, merged);
            long result = db.insertWithOnConflict(TABLE_MEDIA_METADATA, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            return result != -1;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }
    }

    public Cursor getMediaMetadataByMediaId(int mediaId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_MEDIA_METADATA + " WHERE " + COL_METADATA_MEDIA_ID + " = ?",
                new String[]{String.valueOf(mediaId)});
    }

    public String getPreferredDisplayTitle(int mediaId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT m." + COL_TITLE + ", mm." + COL_METADATA_CANONICAL_TITLE + " " +
                            "FROM " + TABLE_MEDIA + " m " +
                            "LEFT JOIN " + TABLE_MEDIA_METADATA + " mm ON m." + COL_ID + " = mm." + COL_METADATA_MEDIA_ID + " " +
                            "WHERE m." + COL_ID + "=?",
                    new String[]{String.valueOf(mediaId)}
            );
            if (cursor.moveToFirst()) {
                String canonical = cursor.getString(1);
                if (canonical != null && !canonical.trim().isEmpty()) {
                    return canonical.trim();
                }
                return cursor.getString(0);
            }
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }
    }

    public int findMediaIdByMetadataCandidate(String detectedTitle, Map<String, String> externalIds) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT m." + COL_ID + ", m." + COL_TITLE + ", " +
                            "mm." + COL_METADATA_CANONICAL_TITLE + ", " +
                            "mm." + COL_METADATA_NORMALIZED_TITLE + ", " +
                            "mm." + COL_METADATA_ALT_TITLES_JSON + ", " +
                            "mm." + COL_METADATA_EXTERNAL_IDS_JSON + " " +
                            "FROM " + TABLE_MEDIA + " m " +
                            "LEFT JOIN " + TABLE_MEDIA_METADATA + " mm ON m." + COL_ID + " = mm." + COL_METADATA_MEDIA_ID + " " +
                            "WHERE m." + COL_STATUS + " != 'Recently Deleted'",
                    null
            );

            String normalizedDetected = MediaMetadataProfile.normalizeTitle(detectedTitle);
            int bestId = -1;
            int bestScore = -1;

            while (cursor.moveToNext()) {
                int mediaId = cursor.getInt(0);
                String dbTitle = cursor.getString(1);
                String canonicalTitle = cursor.getString(2);
                String normalizedTitle = cursor.getString(3);
                String altTitlesJson = cursor.getString(4);
                String externalIdsJson = cursor.getString(5);

                int score = 0;

                if (externalIds != null && !externalIds.isEmpty()) {
                    Map<String, String> storedIds = parseJsonObject(externalIdsJson);
                    for (Map.Entry<String, String> entry : externalIds.entrySet()) {
                        String key = entry.getKey();
                        String value = entry.getValue();
                        if (key == null || value == null) {
                            continue;
                        }
                        String storedValue = storedIds.get(key);
                        if (storedValue != null && storedValue.equalsIgnoreCase(value)) {
                            score += 1000;
                        }
                    }
                }

                if (normalizedDetected != null) {
                    String normalizedDbTitle = MediaMetadataProfile.normalizeTitle(dbTitle);
                    String normalizedCanonical = normalizedTitle != null
                            ? normalizedTitle
                            : MediaMetadataProfile.normalizeTitle(canonicalTitle);
                    if (normalizedDbTitle != null && normalizedDbTitle.equals(normalizedDetected)) {
                        score += 600;
                    }
                    if (normalizedCanonical != null && normalizedCanonical.equals(normalizedDetected)) {
                        score += 700;
                    }
                    List<String> altTitles = parseJsonArray(altTitlesJson);
                    for (String alt : altTitles) {
                        String normalizedAlt = MediaMetadataProfile.normalizeTitle(alt);
                        if (normalizedAlt != null && normalizedAlt.equals(normalizedDetected)) {
                            score += 650;
                            break;
                        }
                    }
                    if (score == 0 && normalizedCanonical != null
                            && (normalizedCanonical.contains(normalizedDetected) || normalizedDetected.contains(normalizedCanonical))) {
                        score += 250;
                    }
                }

                if (score > bestScore) {
                    bestScore = score;
                    bestId = mediaId;
                }
            }

            return bestScore > 0 ? bestId : -1;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }
    }

    private ContentValues toMetadataContentValues(int mediaId, MediaMetadataProfile profile) {
        ContentValues values = new ContentValues();
        values.put(COL_METADATA_MEDIA_ID, mediaId);
        values.put(COL_METADATA_CANONICAL_TITLE, profile.getCanonicalTitle());
        values.put(COL_METADATA_NORMALIZED_TITLE, profile.getNormalizedTitle());
        values.put(COL_METADATA_ALT_TITLES_JSON, profile.getAltTitlesJson());
        values.put(COL_METADATA_PROVIDER_ID, profile.getProviderId());
        values.put(COL_METADATA_PROVIDER_SLUG, profile.getProviderSlug());
        values.put(COL_METADATA_CANONICAL_URL, profile.getCanonicalUrl());
        values.put(COL_METADATA_SOURCE, profile.getMetadataSource());
        values.put(COL_METADATA_MEDIA_TYPE, profile.getMediaType());
        values.put(COL_METADATA_SUB_TYPE, profile.getSubType());
        values.put(COL_METADATA_LANGUAGE, profile.getLanguage());
        values.put(COL_METADATA_REGION, profile.getRegion());
        values.put(COL_METADATA_STATUS, profile.getStatus());
        values.put(COL_METADATA_RELEASE_YEAR, profile.getReleaseYear());
        values.put(COL_METADATA_TOTAL_COUNT, profile.getTotalCount());
        values.put(COL_METADATA_UNIT, profile.getUnit());
        values.put(COL_METADATA_GENRES_JSON, profile.getGenresJson());
        values.put(COL_METADATA_TAGS_JSON, profile.getTagsJson());
        values.put(COL_METADATA_EXTERNAL_IDS_JSON, profile.getExternalIdsJson());
        values.put(COL_METADATA_RATING, profile.getRating());
        values.put(COL_METADATA_POPULARITY, profile.getPopularity());
        values.put(COL_METADATA_PROVIDER_FEATURES_JSON, profile.getProviderFeaturesJson());
        values.put(COL_METADATA_CONFIDENCE, profile.getMetadataConfidence());
        values.put(COL_METADATA_PRIORITY, profile.getMetadataPriority());
        values.put(COL_METADATA_UPDATED_AT,
                profile.getMetadataUpdatedAtIso() != null ? profile.getMetadataUpdatedAtIso() : getDateTime());
        return values;
    }

    private MediaMetadataProfile readMetadataProfile(Cursor cursor) {
        MediaMetadataProfile profile = MediaMetadataProfile.create()
                .withCanonicalTitle(cursor.getString(cursor.getColumnIndexOrThrow(COL_METADATA_CANONICAL_TITLE)))
                .withNormalizedTitle(cursor.getString(cursor.getColumnIndexOrThrow(COL_METADATA_NORMALIZED_TITLE)))
                .withProviderId(cursor.getString(cursor.getColumnIndexOrThrow(COL_METADATA_PROVIDER_ID)))
                .withProviderSlug(cursor.getString(cursor.getColumnIndexOrThrow(COL_METADATA_PROVIDER_SLUG)))
                .withCanonicalUrl(cursor.getString(cursor.getColumnIndexOrThrow(COL_METADATA_CANONICAL_URL)))
                .withMetadataSource(cursor.getString(cursor.getColumnIndexOrThrow(COL_METADATA_SOURCE)))
                .withMediaType(cursor.getString(cursor.getColumnIndexOrThrow(COL_METADATA_MEDIA_TYPE)))
                .withSubType(cursor.getString(cursor.getColumnIndexOrThrow(COL_METADATA_SUB_TYPE)))
                .withLanguage(cursor.getString(cursor.getColumnIndexOrThrow(COL_METADATA_LANGUAGE)))
                .withRegion(cursor.getString(cursor.getColumnIndexOrThrow(COL_METADATA_REGION)))
                .withStatus(cursor.getString(cursor.getColumnIndexOrThrow(COL_METADATA_STATUS)))
                .withReleaseYear(cursor.isNull(cursor.getColumnIndexOrThrow(COL_METADATA_RELEASE_YEAR))
                        ? null
                        : cursor.getInt(cursor.getColumnIndexOrThrow(COL_METADATA_RELEASE_YEAR)))
                .withTotalCount(cursor.isNull(cursor.getColumnIndexOrThrow(COL_METADATA_TOTAL_COUNT))
                        ? null
                        : cursor.getInt(cursor.getColumnIndexOrThrow(COL_METADATA_TOTAL_COUNT)))
                .withUnit(cursor.getString(cursor.getColumnIndexOrThrow(COL_METADATA_UNIT)))
                .withRating(cursor.isNull(cursor.getColumnIndexOrThrow(COL_METADATA_RATING))
                        ? null
                        : cursor.getFloat(cursor.getColumnIndexOrThrow(COL_METADATA_RATING)))
                .withPopularity(cursor.isNull(cursor.getColumnIndexOrThrow(COL_METADATA_POPULARITY))
                        ? null
                        : cursor.getFloat(cursor.getColumnIndexOrThrow(COL_METADATA_POPULARITY)))
                .withProviderFeaturesJson(cursor.getString(cursor.getColumnIndexOrThrow(COL_METADATA_PROVIDER_FEATURES_JSON)))
                .withMetadataConfidence(cursor.isNull(cursor.getColumnIndexOrThrow(COL_METADATA_CONFIDENCE))
                        ? null
                        : cursor.getFloat(cursor.getColumnIndexOrThrow(COL_METADATA_CONFIDENCE)))
                .withMetadataPriority(cursor.isNull(cursor.getColumnIndexOrThrow(COL_METADATA_PRIORITY))
                        ? null
                        : cursor.getInt(cursor.getColumnIndexOrThrow(COL_METADATA_PRIORITY)));

        for (String alt : parseJsonArray(cursor.getString(cursor.getColumnIndexOrThrow(COL_METADATA_ALT_TITLES_JSON)))) {
            profile.addAltTitle(alt);
        }
        for (String genre : parseJsonArray(cursor.getString(cursor.getColumnIndexOrThrow(COL_METADATA_GENRES_JSON)))) {
            profile.addGenre(genre);
        }
        for (String tag : parseJsonArray(cursor.getString(cursor.getColumnIndexOrThrow(COL_METADATA_TAGS_JSON)))) {
            profile.addTag(tag);
        }
        Map<String, String> ids = parseJsonObject(cursor.getString(cursor.getColumnIndexOrThrow(COL_METADATA_EXTERNAL_IDS_JSON)));
        for (Map.Entry<String, String> entry : ids.entrySet()) {
            profile.addExternalId(entry.getKey(), entry.getValue());
        }
        return profile;
    }

    private MediaMetadataProfile mergeMetadataProfiles(MediaMetadataProfile existing, MediaMetadataProfile incoming, String sourceType) {
        String incomingSource = normalizeMetadataSource(sourceType, incoming.getMetadataSource());
        String existingSource = normalizeMetadataSource(existing.getMetadataSource(), existing.getMetadataSource());

        float existingScore = computeMetadataScore(existingSource, existing.getMetadataPriority(), existing.getMetadataConfidence());
        float incomingScore = computeMetadataScore(incomingSource, incoming.getMetadataPriority(), incoming.getMetadataConfidence());
        boolean preferIncoming = incomingScore >= existingScore;

        MediaMetadataProfile merged = MediaMetadataProfile.create()
                .withCanonicalTitle(chooseString(existing.getCanonicalTitle(), incoming.getCanonicalTitle(), preferIncoming))
                .withNormalizedTitle(chooseString(existing.getNormalizedTitle(), incoming.getNormalizedTitle(), preferIncoming))
                .withProviderId(chooseString(existing.getProviderId(), incoming.getProviderId(), preferIncoming))
                .withProviderSlug(chooseString(existing.getProviderSlug(), incoming.getProviderSlug(), preferIncoming))
                .withCanonicalUrl(chooseString(existing.getCanonicalUrl(), incoming.getCanonicalUrl(), preferIncoming))
                .withMetadataSource(preferIncoming ? incomingSource : existingSource)
                .withMediaType(chooseString(existing.getMediaType(), incoming.getMediaType(), preferIncoming))
                .withSubType(chooseString(existing.getSubType(), incoming.getSubType(), preferIncoming))
                .withLanguage(chooseString(existing.getLanguage(), incoming.getLanguage(), preferIncoming))
                .withRegion(chooseString(existing.getRegion(), incoming.getRegion(), preferIncoming))
                .withStatus(chooseString(existing.getStatus(), incoming.getStatus(), preferIncoming))
                .withReleaseYear(chooseInteger(existing.getReleaseYear(), incoming.getReleaseYear(), preferIncoming))
                .withTotalCount(chooseInteger(existing.getTotalCount(), incoming.getTotalCount(), preferIncoming))
                .withUnit(chooseString(existing.getUnit(), incoming.getUnit(), preferIncoming))
                .withRating(chooseFloat(existing.getRating(), incoming.getRating(), preferIncoming))
                .withPopularity(chooseFloat(existing.getPopularity(), incoming.getPopularity(), preferIncoming))
                .withProviderFeaturesJson(mergeJsonObjects(existing.getProviderFeaturesJson(), incoming.getProviderFeaturesJson(), preferIncoming))
                .withMetadataConfidence(preferIncoming
                        ? chooseFloat(existing.getMetadataConfidence(), incoming.getMetadataConfidence(), true)
                        : chooseFloat(existing.getMetadataConfidence(), incoming.getMetadataConfidence(), false))
                .withMetadataPriority(preferIncoming
                        ? chooseInteger(existing.getMetadataPriority(), incoming.getMetadataPriority(), true)
                        : chooseInteger(existing.getMetadataPriority(), incoming.getMetadataPriority(), false))
                .stampNow();

        String mergedAlt = MediaMetadataProfile.mergeJsonArrays(existing.getAltTitlesJson(), incoming.getAltTitlesJson());
        for (String alt : parseJsonArray(mergedAlt)) {
            merged.addAltTitle(alt);
        }

        String mergedGenres = MediaMetadataProfile.mergeJsonArrays(existing.getGenresJson(), incoming.getGenresJson());
        for (String genre : parseJsonArray(mergedGenres)) {
            merged.addGenre(genre);
        }

        String mergedTags = MediaMetadataProfile.mergeJsonArrays(existing.getTagsJson(), incoming.getTagsJson());
        for (String tag : parseJsonArray(mergedTags)) {
            merged.addTag(tag);
        }

        String mergedExternalIds = mergeJsonObjects(existing.getExternalIdsJson(), incoming.getExternalIdsJson(), preferIncoming);
        Map<String, String> externalIds = parseJsonObject(mergedExternalIds);
        for (Map.Entry<String, String> entry : externalIds.entrySet()) {
            merged.addExternalId(entry.getKey(), entry.getValue());
        }
        return merged;
    }

    private float computeMetadataScore(String source, Integer priority, Float confidence) {
        float tier = sourceTier(source);
        float effectivePriority = priority != null ? priority : 0f;
        float effectiveConfidence = confidence != null ? confidence : 0f;
        return (tier * 1000f) + (effectivePriority * 2f) + (effectiveConfidence * 100f);
    }

    private float sourceTier(String source) {
        if (source == null) {
            return 0f;
        }
        String normalized = source.trim().toLowerCase(Locale.US);
        if (normalized.isEmpty()) {
            return 0f;
        }
        if (normalized.contains("manual") || normalized.contains("user")) {
            return 4f;
        }
        if (normalized.contains("jikan")
                || normalized.contains("tmdb")
                || normalized.contains("google")
                || normalized.contains("openlibrary")
                || normalized.contains("api")) {
            return 3f;
        }
        if (normalized.contains("provider") || normalized.contains("enrichment")) {
            return 2f;
        }
        if (normalized.contains("accessibility") || normalized.contains("tracker")) {
            return 1f;
        }
        return 0.5f;
    }

    private String normalizeMetadataSource(String requestedSource, String profileSource) {
        String first = (requestedSource != null) ? requestedSource.trim() : "";
        if (!first.isEmpty()) {
            return first.toLowerCase(Locale.US);
        }
        String fallback = (profileSource != null) ? profileSource.trim() : "";
        if (!fallback.isEmpty()) {
            return fallback.toLowerCase(Locale.US);
        }
        return "unknown";
    }

    private String chooseString(String existingValue, String incomingValue, boolean preferIncoming) {
        String existing = (existingValue != null && !existingValue.trim().isEmpty()) ? existingValue.trim() : null;
        String incoming = (incomingValue != null && !incomingValue.trim().isEmpty()) ? incomingValue.trim() : null;
        if (existing == null) return incoming;
        if (incoming == null) return existing;
        return preferIncoming ? incoming : existing;
    }

    private Integer chooseInteger(Integer existingValue, Integer incomingValue, boolean preferIncoming) {
        if (existingValue == null) return incomingValue;
        if (incomingValue == null) return existingValue;
        return preferIncoming ? incomingValue : existingValue;
    }

    private Float chooseFloat(Float existingValue, Float incomingValue, boolean preferIncoming) {
        if (existingValue == null) return incomingValue;
        if (incomingValue == null) return existingValue;
        return preferIncoming ? incomingValue : existingValue;
    }

    private List<String> parseJsonArray(String json) {
        List<String> values = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return values;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, null);
                if (value != null && !value.trim().isEmpty()) {
                    values.add(value.trim());
                }
            }
        } catch (JSONException ignored) {
        }
        return values;
    }

    private Map<String, String> parseJsonObject(String json) {
        Map<String, String> values = new LinkedHashMap<>();
        if (json == null || json.trim().isEmpty()) {
            return values;
        }
        try {
            JSONObject object = new JSONObject(json);
            JSONArray names = object.names();
            if (names == null) {
                return values;
            }
            for (int i = 0; i < names.length(); i++) {
                String key = names.optString(i, null);
                if (key == null || key.trim().isEmpty()) {
                    continue;
                }
                String value = object.optString(key, null);
                if (value != null && !value.trim().isEmpty()) {
                    values.put(key.trim(), value.trim());
                }
            }
        } catch (JSONException ignored) {
        }
        return values;
    }

    private String mergeJsonObjects(String existingJson, String incomingJson, boolean preferIncoming) {
        Map<String, String> existing = parseJsonObject(existingJson);
        Map<String, String> incoming = parseJsonObject(incomingJson);
        Map<String, String> merged = new LinkedHashMap<>(existing);
        for (Map.Entry<String, String> entry : incoming.entrySet()) {
            String key = entry.getKey();
            if (!merged.containsKey(key) || preferIncoming) {
                merged.put(key, entry.getValue());
            }
        }
        return new JSONObject(merged).toString();
    }

    private String getDateTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        Date date = new Date();
        return dateFormat.format(date);
    }

    private float getCurrentProgress(SQLiteDatabase db, int id) {
        float oldProgress = 0;
        Cursor cursor = db.query(TABLE_MEDIA, new String[]{COL_CURRENT_PROGRESS},
                COL_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                oldProgress = cursor.getFloat(0);
            }
            cursor.close();
        }
        return oldProgress;
    }

    private void logProgressDelta(SQLiteDatabase db, int mediaId, float delta) {
        if (delta <= 0) {
            return;
        }
        ContentValues logValues = new ContentValues();
        logValues.put(COL_LOG_MEDIA_ID, mediaId);
        logValues.put(COL_PROGRESS_ADDED, delta);
        db.insert(TABLE_PROGRESS_LOG, null, logValues);
        SupabaseMediaSyncManager.enqueueProgressLog(context, mediaId, delta, getDateOffset(0));
    }

    private void logProgressDeltaAtDate(SQLiteDatabase db, int mediaId, float delta, String date) {
        if (delta <= 0) {
            return;
        }
        ContentValues logValues = new ContentValues();
        logValues.put(COL_LOG_MEDIA_ID, mediaId);
        logValues.put(COL_PROGRESS_ADDED, delta);
        logValues.put(COL_LOG_DATE, date);
        db.insert(TABLE_PROGRESS_LOG, null, logValues);
        SupabaseMediaSyncManager.enqueueProgressLog(context, mediaId, delta, date);
    }

    private void seedProgressHistory(SQLiteDatabase db, int mediaId, float progress) {
        if (progress <= 0) {
            return;
        }

        float daily = Math.max(0.1f, progress / 5.0f);
        float weekly = Math.max(0.1f, progress / 3.0f);
        float monthly = progress - daily - weekly;

        if (monthly < 0.1f) {
            monthly = 0.1f;
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
                float progress = cursor.getFloat(1);
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
        float pages = 0;
        float episodes = 0;
        float minutes = 0;

        String query = "SELECT m." + COL_UNIT + ", SUM(p." + COL_PROGRESS_ADDED + ") " +
                "FROM " + TABLE_PROGRESS_LOG + " p " +
                "JOIN " + TABLE_MEDIA + " m ON p." + COL_LOG_MEDIA_ID + " = m." + COL_ID + " " +
                "WHERE p." + COL_LOG_DATE + " LIKE ? " +
                "GROUP BY m." + COL_UNIT;

        Cursor cursor = db.rawQuery(query, new String[]{datePattern + "%"});

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String unit = cursor.getString(0);
                float progress = cursor.getFloat(1);

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

    /**
     * Returns goal completion history for the last N days (oldest -> newest).
     * 1 means daily goal met, 0 means not met or no row present.
     */
    public int[] getRecentGoalHistory(int days) {
        if (days <= 0) {
            return new int[0];
        }

        int[] history = new int[days];
        SQLiteDatabase db = this.getReadableDatabase();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar cal = Calendar.getInstance();

        for (int index = days - 1; index >= 0; index--) {
            String dateStr = sdf.format(cal.getTime());
            try (Cursor cursor = db.rawQuery(
                    "SELECT " + COL_DAILY_GOAL_MET + " FROM " + TABLE_DAILY_METRICS +
                            " WHERE " + COL_DAILY_DATE + " = ?",
                    new String[]{dateStr}
            )) {
                if (cursor != null && cursor.moveToFirst()) {
                    history[index] = cursor.getInt(0) == 1 ? 1 : 0;
                } else {
                    history[index] = 0;
                }
            }
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        return history;
    }
}
