package com.example.mediavault.api.consumet;

import android.util.Log;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Synchronous anime streaming link resolver using Consumet API.
 * MUST be called from a background thread (AppExecutor.networkIO()).
 * 
 * Usage:
 *   AnimeResolver resolver = new AnimeResolver();
 *   String streamUrl = resolver.resolveStreamingUrl("One Piece", 1);
 */
public class AnimeResolver {
    private static final String TAG = "AnimeResolver";
    private static final String CONSUMET_BASE_URL = "https://api.consumet.org/";
    private static final int TIMEOUT_SECONDS = 15;
    
    private final ConsumetApiService apiService;
    
    public AnimeResolver() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(CONSUMET_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        
        apiService = retrofit.create(ConsumetApiService.class);
    }
    
    /**
     * Resolves the highest quality streaming URL for an anime episode.
     * 
     * @param animeTitle The title of the anime (e.g., "One Piece")
     * @param episodeNumber The episode number (e.g., 1)
     * @return The streaming URL (.m3u8 or .mp4), or null if resolution fails
     */
    public String resolveStreamingUrl(String animeTitle, int episodeNumber) {
        if (animeTitle == null || animeTitle.trim().isEmpty()) {
            Log.e(TAG, "RESOLUTION FAILED: Invalid anime title");
            return null;
        }
        
        try {
            // Step 1: Search for the anime
            String searchQuery = formatSearchQuery(animeTitle);
            Log.d(TAG, "═══════════════════════════════════════════════════");
            Log.d(TAG, "RESOLUTION START: " + animeTitle + " Episode " + episodeNumber);
            Log.d(TAG, "Step 1/5: Searching Consumet API with query: " + searchQuery);
            
            Response<ConsumetSearchResponse> searchResponse;
            try {
                searchResponse = apiService.searchAnime(searchQuery).execute();
            } catch (Exception e) {
                Log.e(TAG, "❌ SEARCH API CALL FAILED (timeout or network error): " + e.getMessage());
                return null;
            }
            
            if (!searchResponse.isSuccessful()) {
                Log.e(TAG, "❌ SEARCH FAILED: HTTP " + searchResponse.code() + " - " + searchResponse.message());
                if (searchResponse.errorBody() != null) {
                    try {
                        Log.e(TAG, "Error body: " + searchResponse.errorBody().string());
                    } catch (IOException ignored) {}
                }
                return null;
            }
            
            ConsumetSearchResponse searchBody = searchResponse.body();
            if (searchBody == null || searchBody.getResults() == null || searchBody.getResults().isEmpty()) {
                Log.e(TAG, "❌ NO RESULTS: Consumet returned 0 results for: " + animeTitle);
                return null;
            }
            
            Log.d(TAG, "✓ Found " + searchBody.getResults().size() + " potential matches");
            for (int i = 0; i < Math.min(3, searchBody.getResults().size()); i++) {
                ConsumetSearchResponse.SearchResult r = searchBody.getResults().get(i);
                Log.d(TAG, "  Result " + (i+1) + ": " + r.getTitle() + " (ID: " + r.getId() + ")");
            }
            
            // Step 2: Get best result
            ConsumetSearchResponse.SearchResult anime = findBestMatch(searchBody.getResults(), animeTitle);
            if (anime == null) {
                Log.e(TAG, "❌ NO MATCH: None of the results matched query well enough");
                return null;
            }
            String animeId = anime.getId();
            Log.d(TAG, "Step 2/5: Selected: " + anime.getTitle() + " (ID: " + animeId + ")");

            // Step 3: Resolve episode ID via anime info endpoint
            Log.d(TAG, "Step 3/5: Fetching episode list from info endpoint");
            String episodeId = resolveEpisodeId(animeId, episodeNumber);
            if (episodeId == null) {
                // Fallback to legacy pattern
                episodeId = animeId + "-episode-" + episodeNumber;
                Log.w(TAG, "⚠ Info endpoint failed, using fallback pattern: " + episodeId);
            } else {
                Log.d(TAG, "✓ Resolved episode ID: " + episodeId);
            }
            
            // Step 4: Get streaming links
            Log.d(TAG, "Step 4/5: Fetching streaming sources from watch endpoint");
            Response<ConsumetStreamResponse> streamResponse;
            try {
                streamResponse = apiService.getStreamingLinks(episodeId).execute();
            } catch (Exception e) {
                Log.e(TAG, "❌ STREAM API CALL FAILED (timeout or network error): " + e.getMessage());
                return null;
            }
            
            if (!streamResponse.isSuccessful()) {
                Log.e(TAG, "❌ STREAM FETCH FAILED: HTTP " + streamResponse.code() + " - " + streamResponse.message());
                if (streamResponse.errorBody() != null) {
                    try {
                        Log.e(TAG, "Error body: " + streamResponse.errorBody().string());
                    } catch (IOException ignored) {}
                }
                return null;
            }
            
            ConsumetStreamResponse streamBody = streamResponse.body();
            if (streamBody == null || streamBody.getSources() == null || streamBody.getSources().isEmpty()) {
                Log.e(TAG, "❌ NO SOURCES: API returned empty sources array for episode: " + episodeId);
                return null;
            }
            
            Log.d(TAG, "✓ Found " + streamBody.getSources().size() + " streaming sources");
            
            // Step 5: Find highest quality source
            Log.d(TAG, "Step 5/5: Selecting best quality source");
            String bestUrl = findBestQualitySource(streamBody.getSources());
            
            if (bestUrl != null) {
                Log.d(TAG, "✓✓✓ RESOLUTION SUCCESS ✓✓✓");
                Log.d(TAG, "Stream URL: " + bestUrl);
                Log.d(TAG, "═══════════════════════════════════════════════════");
            } else {
                Log.e(TAG, "❌ EXTRACTION FAILED: Could not extract URL from sources");
            }
            
            return bestUrl;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ UNEXPECTED ERROR during resolution", e);
            return null;
        }
    }

    /**
     * Fetches episodes list for an anime title.
     */
    public List<ConsumetAnimeInfoResponse.Episode> fetchEpisodes(String animeTitle) {
        if (animeTitle == null || animeTitle.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            String searchQuery = formatSearchQuery(animeTitle);
            Response<ConsumetSearchResponse> searchResponse = apiService.searchAnime(searchQuery).execute();
            if (!searchResponse.isSuccessful() || searchResponse.body() == null || searchResponse.body().getResults() == null) {
                return new ArrayList<>();
            }

            List<ConsumetSearchResponse.SearchResult> results = searchResponse.body().getResults();
            if (results.isEmpty()) {
                return new ArrayList<>();
            }

            ConsumetSearchResponse.SearchResult anime = findBestMatch(results, animeTitle);
            if (anime == null || anime.getId() == null) {
                return new ArrayList<>();
            }

            Response<ConsumetAnimeInfoResponse> infoResponse = apiService.getAnimeInfo(anime.getId()).execute();
            if (!infoResponse.isSuccessful() || infoResponse.body() == null || infoResponse.body().getEpisodes() == null) {
                return new ArrayList<>();
            }

            return infoResponse.body().getEpisodes();
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch episodes", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Formats anime title for Consumet GogoAnime search.
     * Converts to lowercase and replaces spaces with hyphens.
     * Example: "One Piece" -> "one-piece"
     */
    private String formatSearchQuery(String title) {
        return title.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9-]", "");
    }

    private String resolveEpisodeId(String animeId, int episodeNumber) {
        try {
            Response<ConsumetAnimeInfoResponse> infoResponse = apiService.getAnimeInfo(animeId).execute();
            if (!infoResponse.isSuccessful() || infoResponse.body() == null || infoResponse.body().getEpisodes() == null) {
                return null;
            }

            for (ConsumetAnimeInfoResponse.Episode episode : infoResponse.body().getEpisodes()) {
                if (episode != null && episode.getEpisodeNumber() == episodeNumber && episode.getId() != null) {
                    return episode.getId();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to resolve episode id from info endpoint", e);
        }
        return null;
    }

    private ConsumetSearchResponse.SearchResult findBestMatch(
            List<ConsumetSearchResponse.SearchResult> results,
            String queryTitle
    ) {
        if (results == null || results.isEmpty()) return null;
        String normalizedQuery = normalize(queryTitle);
        ConsumetSearchResponse.SearchResult best = results.get(0);

        for (ConsumetSearchResponse.SearchResult result : results) {
            if (result == null || result.getTitle() == null) continue;
            String normalizedTitle = normalize(result.getTitle());
            if (normalizedTitle.equals(normalizedQuery)) {
                return result;
            }
            if (normalizedTitle.contains(normalizedQuery) || normalizedQuery.contains(normalizedTitle)) {
                best = result;
            }
        }

        return best;
    }

    private String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
    
    /**
     * Finds the highest quality streaming source.
     * Priority: 1080p > 720p > default > backup > any .m3u8 > any .mp4
     */
    private String findBestQualitySource(List<ConsumetStreamResponse.Source> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        
        ConsumetStreamResponse.Source best1080p = null;
        ConsumetStreamResponse.Source best720p = null;
        ConsumetStreamResponse.Source bestDefault = null;
        ConsumetStreamResponse.Source bestM3U8 = null;
        ConsumetStreamResponse.Source bestMP4 = null;
        ConsumetStreamResponse.Source fallback = null;
        
        for (ConsumetStreamResponse.Source source : sources) {
            String quality = source.getQuality();
            String url = source.getUrl();
            
            if (url == null || url.isEmpty()) {
                continue;
            }
            
            // Track first valid source as fallback
            if (fallback == null) {
                fallback = source;
            }
            
            // Priority by quality
            if (quality != null) {
                String lowerQuality = quality.toLowerCase(Locale.ROOT);
                if (lowerQuality.contains("1080")) {
                    best1080p = source;
                } else if (lowerQuality.contains("720")) {
                    best720p = source;
                } else if (lowerQuality.contains("default")) {
                    bestDefault = source;
                }
            }
            
            // Track by format
            if (source.isM3U8() && bestM3U8 == null) {
                bestM3U8 = source;
            } else if (!source.isM3U8() && url.endsWith(".mp4") && bestMP4 == null) {
                bestMP4 = source;
            }
        }
        
        // Return in priority order
        if (best1080p != null) return best1080p.getUrl();
        if (best720p != null) return best720p.getUrl();
        if (bestDefault != null) return bestDefault.getUrl();
        if (bestM3U8 != null) return bestM3U8.getUrl();
        if (bestMP4 != null) return bestMP4.getUrl();
        if (fallback != null) return fallback.getUrl();
        
        return null;
    }
    
    /**
     * Alternative method that accepts a custom episode ID format.
     * Use this if you already have the GogoAnime episode ID.
     * 
     * @param episodeId The full episode ID (e.g., "one-piece-episode-1000")
     * @return The streaming URL, or null if resolution fails
     */
    public String resolveByEpisodeId(String episodeId) {
        if (episodeId == null || episodeId.trim().isEmpty()) {
            Log.e(TAG, "Invalid episode ID");
            return null;
        }
        
        try {
            Log.d(TAG, "Fetching streaming links for episode ID: " + episodeId);
            
            Response<ConsumetStreamResponse> streamResponse = apiService.getStreamingLinks(episodeId).execute();
            
            if (!streamResponse.isSuccessful()) {
                Log.e(TAG, "Stream fetch failed with code: " + streamResponse.code());
                return null;
            }
            
            ConsumetStreamResponse streamBody = streamResponse.body();
            if (streamBody == null || streamBody.getSources() == null || streamBody.getSources().isEmpty()) {
                Log.e(TAG, "No streaming sources found");
                return null;
            }
            
            return findBestQualitySource(streamBody.getSources());
            
        } catch (IOException e) {
            Log.e(TAG, "Network error during resolution", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during resolution", e);
            return null;
        }
    }
}
