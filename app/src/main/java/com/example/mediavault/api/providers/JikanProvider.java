package com.example.mediavault.api.providers;

import android.util.Log;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Jikan metadata + external anime fallback provider.
 * 
 * Uses Jikan API for metadata (search, episode list) and
 * external fallback URL extraction for streaming handoff.
 * 
 * Flow:
 * 1. Search anime via Jikan to get metadata
 * 2. Generate fallback slug from title
 * 3. Scrape fallback page for streaming URL
 */
public class JikanProvider implements MediaProvider {
    private static final String TAG = "JikanProvider";
    private static final String PROVIDER_NAME = "Jikan+AnimeFallback";
    private static final String JIKAN_BASE_URL = "https://api.jikan.moe/v4/";
    private static final String GOGOANIME_BASE_URL = "https://aniwatchtv.to/";
    private static final int TIMEOUT_SECONDS = 15;
    
    // GogoAnime URL patterns
    private static final Pattern STREAMING_PATTERN = Pattern.compile(
        "data-video=\"(https?://[^\"]+)\"",
        Pattern.CASE_INSENSITIVE
    );
    
    private final JikanApiService jikanApi;
    private final OkHttpClient httpClient;
    
    public JikanProvider() {
        httpClient = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();
        
        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(JIKAN_BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build();
        
        jikanApi = retrofit.create(JikanApiService.class);
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
        return type == MediaType.ANIME;
    }
    
    @Override
    public String resolveAnimeEpisode(String title, int episode) {
        Log.d(TAG, "Resolving anime: " + title + " Ep " + episode);
        
        try {
            // Generate GogoAnime slug directly from title
            String slug = titleToGogoSlug(title);
            String streamUrl = scrapeGogoAnime(slug, episode);
            
            if (streamUrl != null) {
                Log.d(TAG, "Found stream URL: " + streamUrl);
                return streamUrl;
            }
            
            // Try with English title from Jikan
            JikanApiService.AnimeSearchResponse response = jikanApi.searchAnime(title, 5).execute().body();
            if (response != null && response.getData() != null && !response.getData().isEmpty()) {
                JikanApiService.AnimeData anime = response.getData().get(0);
                
                // Try English title
                if (anime.getTitleEnglish() != null) {
                    slug = titleToGogoSlug(anime.getTitleEnglish());
                    streamUrl = scrapeGogoAnime(slug, episode);
                    if (streamUrl != null) {
                        return streamUrl;
                    }
                }
                
                // Try original title
                slug = titleToGogoSlug(anime.getTitle());
                streamUrl = scrapeGogoAnime(slug, episode);
                if (streamUrl != null) {
                    return streamUrl;
                }
            }
            
            Log.e(TAG, "Could not find streaming URL");
            return null;
            
        } catch (Exception e) {
            Log.e(TAG, "Jikan+GogoAnime resolution failed", e);
            return null;
        }
    }
    
    @Override
    public List<EpisodeInfo> fetchAnimeEpisodes(String title) {
        Log.d(TAG, "Fetching episodes for: " + title);
        
        try {
            // Search for anime
            retrofit2.Response<JikanApiService.AnimeSearchResponse> searchResponse = 
                jikanApi.searchAnime(title, 5).execute();
                
            if (!searchResponse.isSuccessful() || searchResponse.body() == null) {
                return new ArrayList<>();
            }
            
            List<JikanApiService.AnimeData> results = searchResponse.body().getData();
            if (results == null || results.isEmpty()) {
                return new ArrayList<>();
            }
            
            // Find best match
            JikanApiService.AnimeData anime = findBestMatch(results, title);
            if (anime == null) {
                return new ArrayList<>();
            }
            
            int malId = anime.getMalId();
            Log.d(TAG, "Found anime MAL ID: " + malId);
            
            // Fetch all episodes
            List<EpisodeInfo> allEpisodes = new ArrayList<>();
            int page = 1;
            boolean hasMore = true;
            
            while (hasMore) {
                // Rate limit: 3 req/sec
                if (page > 1) {
                    Thread.sleep(350);
                }
                
                retrofit2.Response<JikanApiService.EpisodeListResponse> epResponse = 
                    jikanApi.getAnimeEpisodes(malId, page).execute();
                    
                if (!epResponse.isSuccessful() || epResponse.body() == null) {
                    break;
                }
                
                List<JikanApiService.EpisodeData> episodes = epResponse.body().getData();
                if (episodes == null || episodes.isEmpty()) {
                    break;
                }
                
                for (JikanApiService.EpisodeData ep : episodes) {
                    allEpisodes.add(new EpisodeInfo(
                        String.valueOf(ep.getMalId()),
                        ep.getMalId(),
                        ep.getTitle() != null ? ep.getTitle() : "Episode " + ep.getMalId(),
                        null // URL will be resolved on demand
                    ));
                }
                
                hasMore = epResponse.body().getPagination() != null && 
                          epResponse.body().getPagination().hasNextPage();
                page++;
                
                // Safety limit
                if (page > 10) break;
            }
            
            Log.d(TAG, "Fetched " + allEpisodes.size() + " episodes");
            return allEpisodes;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch episodes", e);
            return new ArrayList<>();
        }
    }
    
    // ============= HELPER METHODS =============
    
    private String titleToGogoSlug(String title) {
        if (title == null) return "";
        
        return title.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\s-]", "") // Remove special chars
            .replaceAll("\\s+", "-")          // Replace spaces with hyphens
            .replaceAll("-+", "-")            // Collapse multiple hyphens
            .replaceAll("^-|-$", "");         // Trim leading/trailing hyphens
    }
    
    private String scrapeGogoAnime(String slug, int episode) throws IOException {
        // GogoAnime episode URL format
        String episodeUrl = GOGOANIME_BASE_URL + slug + "-episode-" + episode;
        Log.d(TAG, "Scraping: " + episodeUrl);
        
        Request request = new Request.Builder()
            .url(episodeUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Log.w(TAG, "GogoAnime request failed: " + response.code());
                return null;
            }
            
            String html = response.body() != null ? response.body().string() : "";
            
            // Extract streaming URL from page
            Matcher matcher = STREAMING_PATTERN.matcher(html);
            if (matcher.find()) {
                return matcher.group(1);
            }
            
            // Try alternative pattern
            Pattern altPattern = Pattern.compile("iframe[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
            Matcher altMatcher = altPattern.matcher(html);
            if (altMatcher.find()) {
                String iframeSrc = altMatcher.group(1);
                // Make sure it's a video embed
                if (iframeSrc.contains("streaming") || iframeSrc.contains("embed") || 
                    iframeSrc.contains("player") || iframeSrc.contains("video")) {
                    return iframeSrc;
                }
            }
        }
        
        return null;
    }
    
    private JikanApiService.AnimeData findBestMatch(List<JikanApiService.AnimeData> results, String query) {
        String normalizedQuery = normalize(query);
        
        for (JikanApiService.AnimeData anime : results) {
            String normalizedTitle = normalize(anime.getTitle());
            String normalizedEnglish = normalize(anime.getTitleEnglish());
            
            if (normalizedTitle.equals(normalizedQuery) || normalizedEnglish.equals(normalizedQuery)) {
                return anime;
            }
        }
        
        // Return first result if no exact match
        return results.isEmpty() ? null : results.get(0);
    }
    
    private String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "")
            .trim();
    }
}
