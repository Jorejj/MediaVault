package com.example.mediavault.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import com.example.mediavault.BuildConfig;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class ApiHealthManager {
    public static final String KEY_MAL = "myanimelist";
    public static final String KEY_JIKAN = "jikan";
    public static final String KEY_GOOGLE_BOOKS = "google_books";
    public static final String KEY_OPEN_LIBRARY = "openlibrary";
    public static final String KEY_TMDB = "tmdb";
    public static final String KEY_OMDB = "omdb";
    public static final String KEY_TVMAZE = "tvmaze";
    public static final String KEY_KITSU = "kitsu";
    public static final String KEY_ANILIST = "anilist";

    private static final String PREFS = "api_health_status";
    private static final long STALE_MS = 6L * 60L * 60L * 1000L;

    private ApiHealthManager() {}

    public static void preflightAsync(Context context) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();
        new Thread(() -> preflight(appContext), "api-health-preflight").start();
    }

    public static boolean isHealthy(Context context, String key, boolean defaultValue) {
        if (context == null || TextUtils.isEmpty(key)) return defaultValue;
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long checkedAt = prefs.getLong(key + "_checked_at", 0L);
        if (checkedAt <= 0L || (System.currentTimeMillis() - checkedAt) > STALE_MS) {
            return defaultValue;
        }
        return prefs.getBoolean(key + "_healthy", defaultValue);
    }

    public static void markHealth(Context context, String key, boolean healthy) {
        if (context == null || TextUtils.isEmpty(key)) return;
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(key + "_healthy", healthy)
                .putLong(key + "_checked_at", System.currentTimeMillis())
                .apply();
    }

    private static void preflight(Context context) {
        markHealth(context, KEY_JIKAN, testGet("https://api.jikan.moe/v4/anime?q=naruto&limit=1"));
        markHealth(context, KEY_OPEN_LIBRARY, testGet("https://openlibrary.org/search.json?title=naruto&limit=1"));
        markHealth(context, KEY_KITSU, testGet("https://kitsu.io/api/edge/anime?filter[text]=naruto&page[limit]=1"));
        markHealth(context, KEY_TVMAZE, testGet("https://api.tvmaze.com/search/shows?q=naruto"));

        if (!TextUtils.isEmpty(BuildConfig.MYANIMELIST_CLIENT_ID)) {
            markHealth(context, KEY_MAL, testMal(BuildConfig.MYANIMELIST_CLIENT_ID));
        } else {
            markHealth(context, KEY_MAL, false);
        }

        if (!TextUtils.isEmpty(BuildConfig.GOOGLE_BOOKS_API_KEY)) {
            String url = "https://www.googleapis.com/books/v1/volumes?q=naruto&maxResults=1&key="
                    + Uri.encode(BuildConfig.GOOGLE_BOOKS_API_KEY);
            markHealth(context, KEY_GOOGLE_BOOKS, testGet(url));
        } else {
            markHealth(context, KEY_GOOGLE_BOOKS, false);
        }

        if (!TextUtils.isEmpty(BuildConfig.TMDB_API_KEY)) {
            String url = "https://api.themoviedb.org/3/search/movie?query=naruto&api_key="
                    + Uri.encode(BuildConfig.TMDB_API_KEY);
            markHealth(context, KEY_TMDB, testGet(url));
        } else {
            markHealth(context, KEY_TMDB, false);
        }

        if (!TextUtils.isEmpty(BuildConfig.OMDB_API_KEY)) {
            String url = "https://www.omdbapi.com/?apikey=" + Uri.encode(BuildConfig.OMDB_API_KEY) + "&s=naruto&type=movie";
            markHealth(context, KEY_OMDB, testGet(url));
        } else {
            markHealth(context, KEY_OMDB, false);
        }

        markHealth(context, KEY_ANILIST, testAniList());
    }

    private static boolean testGet(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(6000);
            connection.setRequestMethod("GET");
            int code = connection.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static boolean testMal(String clientId) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("https://api.myanimelist.net/v2/anime?q=naruto&limit=1").openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(6000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("X-MAL-CLIENT-ID", clientId);
            int code = connection.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static boolean testAniList() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("https://graphql.anilist.co").openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(6000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            String body = "{\"query\":\"query{Page(page:1,perPage:1){media(search:\\\"naruto\\\",type:ANIME){id}}}\"}";
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload);
            }
            int code = connection.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
