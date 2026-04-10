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
 * Synchronous movie streaming link resolver using Consumet API (FlixHQ).
 * MUST be called from a background thread (AppExecutor.networkIO()).
 * 
 * Usage:
 *   MovieResolver resolver = new MovieResolver();
 *   String streamUrl = resolver.resolveMovie("Inception", 2010);
 */
public class MovieResolver {
    private static final String TAG = "MovieResolver";
    private static final String CONSUMET_BASE_URL = "https://api.consumet.org/";
    private static final int TIMEOUT_SECONDS = 20;
    
    private final ConsumetApiService apiService;
    
    public MovieResolver() {
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
     * Resolves streaming URL for a movie.
     * 
     * @param movieTitle The movie title (e.g., "Inception")
     * @param year Optional year for better matching (e.g., 2010), pass 0 to ignore
     * @return Streaming URL (.m3u8 or .mp4), or null if resolution fails
     */
    public String resolveMovie(String movieTitle, int year) {
        if (movieTitle == null || movieTitle.trim().isEmpty()) {
            Log.e(TAG, "Invalid movie title");
            return null;
        }
        
        try {
            // Step 1: Search for movie
            String searchQuery = formatSearchQuery(movieTitle);
            Log.d(TAG, "Searching for movie: " + searchQuery);
            
            Response<ConsumetMovieSearchResponse> searchResponse = apiService.searchMovies(searchQuery).execute();
            
            if (!searchResponse.isSuccessful()) {
                Log.e(TAG, "Movie search failed with code: " + searchResponse.code());
                return null;
            }
            
            ConsumetMovieSearchResponse searchBody = searchResponse.body();
            if (searchBody == null || searchBody.getResults() == null || searchBody.getResults().isEmpty()) {
                Log.e(TAG, "No movies found for: " + movieTitle);
                return null;
            }
            
            // Step 2: Find best match (prefer "Movie" type and matching year)
            ConsumetMovieSearchResponse.MovieResult bestMatch = findBestMovieMatch(
                searchBody.getResults(), 
                year
            );
            
            if (bestMatch == null) {
                Log.e(TAG, "No suitable movie match found");
                return null;
            }
            
            String mediaId = bestMatch.getId();
            Log.d(TAG, "Found movie: " + bestMatch.getTitle() + " (ID: " + mediaId + ")");
            
            // Step 3: Get movie info with episode IDs
            Response<ConsumetMovieInfoResponse> infoResponse = apiService.getMovieInfo(mediaId).execute();
            
            if (!infoResponse.isSuccessful()) {
                Log.e(TAG, "Movie info fetch failed with code: " + infoResponse.code());
                return null;
            }
            
            ConsumetMovieInfoResponse infoBody = infoResponse.body();
            if (infoBody == null || infoBody.getEpisodes() == null || infoBody.getEpisodes().isEmpty()) {
                Log.e(TAG, "No episodes/streams found for movie");
                return null;
            }
            
            // For movies, typically there's only one "episode"
            String episodeId = infoBody.getEpisodes().get(0).getId();
            Log.d(TAG, "Fetching streaming links for: " + episodeId);
            
            // Step 4: Get streaming links
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
            
            // Step 5: Find highest quality source
            String bestUrl = findBestQualitySource(streamBody.getSources());
            
            if (bestUrl != null) {
                Log.d(TAG, "Resolved movie URL: " + bestUrl);
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
     * Find best movie match from search results.
     * Prefer "Movie" type and matching year.
     */
    private ConsumetMovieSearchResponse.MovieResult findBestMovieMatch(
            List<ConsumetMovieSearchResponse.MovieResult> results,
            int targetYear
    ) {
        ConsumetMovieSearchResponse.MovieResult bestMatch = null;
        
        for (ConsumetMovieSearchResponse.MovieResult result : results) {
            // Prioritize "Movie" type
            if ("Movie".equalsIgnoreCase(result.getType())) {
                // If year specified, check for match
                if (targetYear > 0 && result.getReleaseDate() != null) {
                    if (result.getReleaseDate().contains(String.valueOf(targetYear))) {
                        return result; // Perfect match
                    }
                }
                
                // Fallback to first movie if year doesn't match
                if (bestMatch == null) {
                    bestMatch = result;
                }
            }
        }
        
        // If no movie type found, return first result
        return bestMatch != null ? bestMatch : results.get(0);
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
     */
    private String formatSearchQuery(String title) {
        return title.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9-]", "");
    }
}
