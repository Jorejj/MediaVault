package com.example.mediavault.api.providers;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * VidSrc provider for movies and TV shows.
 * 
 * Uses VidSrc embed URLs which automatically resolve streaming.
 * Supports TMDB IDs for accurate content matching.
 * 
 * Embed patterns:
 * - Movie: https://vidsrc.to/embed/movie/{tmdb_id}
 * - TV: https://vidsrc.to/embed/tv/{tmdb_id}/{season}/{episode}
 * 
 * Alternative sources:
 * - https://vidsrc.me/embed/movie?tmdb={id}
 * - https://vidsrc.xyz/embed/movie/{id}
 */
public class VidSrcProvider implements MediaProvider {
    private static final String TAG = "VidSrcProvider";
    private static final String PROVIDER_NAME = "VidSrc";
    
    // Multiple VidSrc mirrors for fallback
    private static final String[] VIDSRC_MIRRORS = {
        "https://vidsrc.to",
        "https://vidsrc.me",
        "https://vidsrc.xyz"
    };
    
    private static final String TMDB_API_KEY = ""; // Optional: for title-to-ID resolution
    private static final String TMDB_BASE_URL = "https://api.themoviedb.org/3/";
    private static final int TIMEOUT_SECONDS = 10;
    
    private final OkHttpClient httpClient;
    private final Gson gson;
    
    public VidSrcProvider() {
        httpClient = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();
        gson = new Gson();
    }
    
    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }
    
    @Override
    public boolean isAvailable() {
        return true;
    }
    
    @Override
    public boolean supportsMediaType(MediaType type) {
        return type == MediaType.MOVIE || type == MediaType.TV_SHOW;
    }
    
    @Override
    public String resolveMovie(String title, int year, String tmdbId) {
        Log.d(TAG, "Resolving movie: " + title + " (" + year + "), TMDB: " + tmdbId);
        
        String resolvedTmdbId = tmdbId;
        
        // If no TMDB ID provided, try to resolve from title
        if (resolvedTmdbId == null || resolvedTmdbId.isEmpty()) {
            resolvedTmdbId = searchTmdbMovie(title, year);
        }
        
        if (resolvedTmdbId == null) {
            Log.e(TAG, "Could not resolve TMDB ID for: " + title);
            return null;
        }
        
        // Try each mirror until one works
        for (String mirror : VIDSRC_MIRRORS) {
            String embedUrl = mirror + "/embed/movie/" + resolvedTmdbId;
            
            if (checkUrlAvailable(embedUrl)) {
                Log.d(TAG, "Found working embed: " + embedUrl);
                return embedUrl;
            }
        }
        
        // Return first mirror URL even if check failed (might work in WebView)
        return VIDSRC_MIRRORS[0] + "/embed/movie/" + resolvedTmdbId;
    }
    
    @Override
    public String resolveTvEpisode(String title, int season, int episode, String tmdbId) {
        Log.d(TAG, "Resolving TV: " + title + " S" + season + "E" + episode + ", TMDB: " + tmdbId);
        
        String resolvedTmdbId = tmdbId;
        
        // If no TMDB ID provided, try to resolve from title
        if (resolvedTmdbId == null || resolvedTmdbId.isEmpty()) {
            resolvedTmdbId = searchTmdbTvShow(title);
        }
        
        if (resolvedTmdbId == null) {
            Log.e(TAG, "Could not resolve TMDB ID for: " + title);
            return null;
        }
        
        // Try each mirror until one works
        for (String mirror : VIDSRC_MIRRORS) {
            String embedUrl = mirror + "/embed/tv/" + resolvedTmdbId + "/" + season + "/" + episode;
            
            if (checkUrlAvailable(embedUrl)) {
                Log.d(TAG, "Found working embed: " + embedUrl);
                return embedUrl;
            }
        }
        
        // Return first mirror URL even if check failed
        return VIDSRC_MIRRORS[0] + "/embed/tv/" + resolvedTmdbId + "/" + season + "/" + episode;
    }
    
    // ============= TMDB SEARCH (Optional) =============
    
    private String searchTmdbMovie(String title, int year) {
        if (TMDB_API_KEY.isEmpty()) {
            // Without API key, try simple IMDB lookup or return null
            return null;
        }
        
        try {
            String url = TMDB_BASE_URL + "search/movie?api_key=" + TMDB_API_KEY + 
                "&query=" + encodeUrl(title) + 
                (year > 0 ? "&year=" + year : "");
            
            Request request = new Request.Builder()
                .url(url)
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) return null;
                
                String json = response.body() != null ? response.body().string() : "";
                TmdbSearchResponse result = gson.fromJson(json, TmdbSearchResponse.class);
                
                if (result != null && result.results != null && !result.results.isEmpty()) {
                    return String.valueOf(result.results.get(0).id);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "TMDB search failed", e);
        }
        
        return null;
    }
    
    private String searchTmdbTvShow(String title) {
        if (TMDB_API_KEY.isEmpty()) {
            return null;
        }
        
        try {
            String url = TMDB_BASE_URL + "search/tv?api_key=" + TMDB_API_KEY + 
                "&query=" + encodeUrl(title);
            
            Request request = new Request.Builder()
                .url(url)
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) return null;
                
                String json = response.body() != null ? response.body().string() : "";
                TmdbSearchResponse result = gson.fromJson(json, TmdbSearchResponse.class);
                
                if (result != null && result.results != null && !result.results.isEmpty()) {
                    return String.valueOf(result.results.get(0).id);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "TMDB search failed", e);
        }
        
        return null;
    }
    
    // ============= HELPER METHODS =============
    
    private boolean checkUrlAvailable(String url) {
        try {
            Request request = new Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "Mozilla/5.0")
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                // 200 or 302 means the embed is available
                if (response.isSuccessful() || response.code() == 302) {
                    return true;
                }
                // Some mirrors block HEAD; verify with a tiny GET before marking unavailable.
                if (response.code() == 403 || response.code() == 405) {
                    return checkUrlAvailableWithGet(url);
                }
                return false;
            }
        } catch (IOException e) {
            Log.w(TAG, "URL check failed: " + url);
            return checkUrlAvailableWithGet(url);
        }
    }

    private boolean checkUrlAvailableWithGet(String url) {
        try {
            Request request = new Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "Mozilla/5.0")
                .header("Range", "bytes=0-0")
                .build();
            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful() || response.code() == 302 || response.code() == 206;
            }
        } catch (IOException e) {
            Log.w(TAG, "GET availability check failed: " + url);
            return false;
        }
    }
    
    private String encodeUrl(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
    
    // TMDB Response classes
    private static class TmdbSearchResponse {
        @SerializedName("results")
        List<TmdbResult> results;
    }
    
    private static class TmdbResult {
        @SerializedName("id")
        int id;
        
        @SerializedName("title")
        String title;
        
        @SerializedName("name")
        String name;
    }
}
