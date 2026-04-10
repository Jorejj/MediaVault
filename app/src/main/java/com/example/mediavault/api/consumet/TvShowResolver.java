package com.example.mediavault.api.consumet;

import android.util.Log;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Synchronous TV show streaming link resolver using Consumet API (FlixHQ).
 * MUST be called from a background thread (AppExecutor.networkIO()).
 * 
 * Usage:
 *   TvShowResolver resolver = new TvShowResolver();
 *   String streamUrl = resolver.resolveTvEpisode("Breaking Bad", 1, 1);
 */
public class TvShowResolver {
    private static final String TAG = "TvShowResolver";
    private static final String CONSUMET_BASE_URL = "https://api.consumet.org/";
    private static final int TIMEOUT_SECONDS = 20;
    
    private final ConsumetApiService apiService;
    
    public TvShowResolver() {
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
     * Resolves streaming URL for a TV show episode.
     * 
     * @param showTitle The TV show title (e.g., "Breaking Bad")
     * @param season Season number (e.g., 1)
     * @param episode Episode number (e.g., 1)
     * @return Streaming URL (.m3u8 or .mp4), or null if resolution fails
     */
    public String resolveTvEpisode(String showTitle, int season, int episode) {
        if (showTitle == null || showTitle.trim().isEmpty()) {
            Log.e(TAG, "Invalid show title");
            return null;
        }
        
        if (season < 1 || episode < 1) {
            Log.e(TAG, "Invalid season/episode numbers");
            return null;
        }
        
        try {
            // Step 1: Search for TV show
            String searchQuery = formatSearchQuery(showTitle);
            Log.d(TAG, "Searching for TV show: " + searchQuery);
            
            Response<ConsumetMovieSearchResponse> searchResponse = apiService.searchMovies(searchQuery).execute();
            
            if (!searchResponse.isSuccessful()) {
                Log.e(TAG, "TV show search failed with code: " + searchResponse.code());
                return null;
            }
            
            ConsumetMovieSearchResponse searchBody = searchResponse.body();
            if (searchBody == null || searchBody.getResults() == null || searchBody.getResults().isEmpty()) {
                Log.e(TAG, "No TV shows found for: " + showTitle);
                return null;
            }
            
            // Step 2: Find best match (prefer "TV Series" type)
            ConsumetMovieSearchResponse.MovieResult bestMatch = findBestTvMatch(searchBody.getResults());
            
            if (bestMatch == null) {
                Log.e(TAG, "No suitable TV show match found");
                return null;
            }
            
            String mediaId = bestMatch.getId();
            Log.d(TAG, "Found TV show: " + bestMatch.getTitle() + " (ID: " + mediaId + ")");
            
            // Step 3: Get TV show info with episodes
            Response<ConsumetMovieInfoResponse> infoResponse = apiService.getMovieInfo(mediaId).execute();
            
            if (!infoResponse.isSuccessful()) {
                Log.e(TAG, "TV show info fetch failed with code: " + infoResponse.code());
                return null;
            }
            
            ConsumetMovieInfoResponse infoBody = infoResponse.body();
            if (infoBody == null || infoBody.getEpisodes() == null || infoBody.getEpisodes().isEmpty()) {
                Log.e(TAG, "No episodes found for TV show");
                return null;
            }
            
            // Step 4: Find the requested episode
            String episodeId = findEpisodeId(infoBody.getEpisodes(), season, episode);
            
            if (episodeId == null) {
                Log.e(TAG, "Episode S" + season + "E" + episode + " not found");
                return null;
            }
            
            Log.d(TAG, "Fetching streaming links for: " + episodeId);
            
            // Step 5: Get streaming links
            Response<ConsumetStreamResponse> streamResponse = apiService.getMovieStreamingLinks(episodeId).execute();
            
            if (!streamResponse.isSuccessful()) {
                Log.e(TAG, "Stream fetch failed with code: " + streamResponse.code());
                return null;
            }
            
            ConsumetStreamResponse streamBody = streamResponse.body();
            if (streamBody == null || streamBody.getSources() == null || streamBody.getSources().isEmpty()) {
                Log.e(TAG, "No streaming sources found");
                return null;
            }
            
            // Step 6: Find highest quality source
            String bestUrl = findBestQualitySource(streamBody.getSources());
            
            if (bestUrl != null) {
                Log.d(TAG, "Resolved episode URL: " + bestUrl);
            } else {
                Log.e(TAG, "Failed to extract URL from sources");
            }
            
            return bestUrl;
            
        } catch (IOException e) {
            Log.e(TAG, "Network error during resolution", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during resolution", e);
            return null;
        }
    }
    
    /**
     * Find best TV show match from search results.
     */
    private ConsumetMovieSearchResponse.MovieResult findBestTvMatch(
            List<ConsumetMovieSearchResponse.MovieResult> results
    ) {
        for (ConsumetMovieSearchResponse.MovieResult result : results) {
            if ("TV Series".equalsIgnoreCase(result.getType())) {
                return result;
            }
        }
        
        // Fallback to first result
        return results.get(0);
    }
    
    /**
     * Find episode ID by season and episode number.
     */
    private String findEpisodeId(
            List<ConsumetMovieInfoResponse.Episode> episodes,
            int targetSeason,
            int targetEpisode
    ) {
        for (ConsumetMovieInfoResponse.Episode ep : episodes) {
            if (ep.getSeason() == targetSeason && ep.getNumber() == targetEpisode) {
                return ep.getId();
            }
        }
        return null;
    }
    
    /**
     * Find highest quality source (same logic as AnimeResolver).
     */
    private String findBestQualitySource(List<ConsumetStreamResponse.Source> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        
        ConsumetStreamResponse.Source best1080p = null;
        ConsumetStreamResponse.Source best720p = null;
        ConsumetStreamResponse.Source bestDefault = null;
        ConsumetStreamResponse.Source bestM3U8 = null;
        ConsumetStreamResponse.Source fallback = null;
        
        for (ConsumetStreamResponse.Source source : sources) {
            String quality = source.getQuality();
            String url = source.getUrl();
            
            if (url == null || url.isEmpty()) continue;
            
            if (fallback == null) fallback = source;
            
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
            
            if (source.isM3U8() && bestM3U8 == null) {
                bestM3U8 = source;
            }
        }
        
        if (best1080p != null) return best1080p.getUrl();
        if (best720p != null) return best720p.getUrl();
        if (bestDefault != null) return bestDefault.getUrl();
        if (bestM3U8 != null) return bestM3U8.getUrl();
        if (fallback != null) return fallback.getUrl();
        
        return null;
    }
    
    /**
     * Format title for search.
     * ISSUE #11 FIX: Use URL encoding
     */
    private String formatSearchQuery(String title) {
        try {
            return java.net.URLEncoder.encode(title.trim(), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return title.trim().replaceAll("\\s+", "%20");
        }
    }
}
