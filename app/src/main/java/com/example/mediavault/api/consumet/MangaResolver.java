package com.example.mediavault.api.consumet;

import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Synchronous manga chapter resolver using Consumet API (MangaDex).
 * MUST be called from a background thread (AppExecutor.networkIO()).
 * 
 * Usage:
 *   MangaResolver resolver = new MangaResolver();
 *   List<String> pageUrls = resolver.resolveChapterPages("One Piece", 1);
 */
public class MangaResolver {
    private static final String TAG = "MangaResolver";
    private static final String CONSUMET_BASE_URL = "https://api.consumet.org/";
    private static final int TIMEOUT_SECONDS = 20;
    
    private final ConsumetApiService apiService;
    
    public MangaResolver() {
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
     * Resolves page URLs for a manga chapter.
     * 
     * @param mangaTitle The manga title (e.g., "One Piece")
     * @param chapterNumber The chapter number (e.g., 1)
     * @return List of image URLs for the chapter, or null if resolution fails
     */
    public List<String> resolveChapterPages(String mangaTitle, int chapterNumber) {
        if (mangaTitle == null || mangaTitle.trim().isEmpty()) {
            Log.e(TAG, "Invalid manga title");
            return null;
        }
        
        try {
            // Step 1: Search for manga
            String searchQuery = formatSearchQuery(mangaTitle);
            Log.d(TAG, "Searching for manga: " + searchQuery);
            
            Response<ConsumetMangaSearchResponse> searchResponse = apiService.searchManga(searchQuery).execute();
            
            if (!searchResponse.isSuccessful()) {
                Log.e(TAG, "Manga search failed with code: " + searchResponse.code());
                return null;
            }
            
            ConsumetMangaSearchResponse searchBody = searchResponse.body();
            if (searchBody == null || searchBody.getResults() == null || searchBody.getResults().isEmpty()) {
                Log.e(TAG, "No manga found for: " + mangaTitle);
                return null;
            }
            
            // Step 2: Get manga ID
            ConsumetMangaSearchResponse.MangaResult manga = searchBody.getResults().get(0);
            String mangaId = manga.getId();
            Log.d(TAG, "Found manga: " + manga.getTitle() + " (ID: " + mangaId + ")");
            
            // Step 3: Get manga info with chapters
            Response<ConsumetMangaInfoResponse> infoResponse = apiService.getMangaInfo(mangaId).execute();
            
            if (!infoResponse.isSuccessful()) {
                Log.e(TAG, "Manga info fetch failed with code: " + infoResponse.code());
                return null;
            }
            
            ConsumetMangaInfoResponse infoBody = infoResponse.body();
            if (infoBody == null || infoBody.getChapters() == null || infoBody.getChapters().isEmpty()) {
                Log.e(TAG, "No chapters found for manga: " + mangaId);
                return null;
            }
            
            // Step 4: Find the requested chapter
            String chapterIdToFetch = findChapterId(infoBody.getChapters(), chapterNumber);
            if (chapterIdToFetch == null) {
                Log.e(TAG, "Chapter " + chapterNumber + " not found");
                return null;
            }
            
            Log.d(TAG, "Fetching chapter: " + chapterIdToFetch);
            
            // Step 5: Get chapter pages
            Response<ConsumetMangaChapterResponse> chapterResponse = apiService.getMangaChapter(chapterIdToFetch).execute();
            
            if (!chapterResponse.isSuccessful()) {
                Log.e(TAG, "Chapter fetch failed with code: " + chapterResponse.code());
                return null;
            }
            
            ConsumetMangaChapterResponse chapterBody = chapterResponse.body();
            if (chapterBody == null || chapterBody.getPages() == null || chapterBody.getPages().isEmpty()) {
                Log.e(TAG, "No pages found for chapter: " + chapterIdToFetch);
                return null;
            }
            
            // Step 6: Extract page URLs
            List<String> pageUrls = new ArrayList<>();
            for (ConsumetMangaChapterResponse.Page page : chapterBody.getPages()) {
                if (page.getImg() != null && !page.getImg().isEmpty()) {
                    pageUrls.add(page.getImg());
                }
            }
            
            Log.d(TAG, "Resolved " + pageUrls.size() + " pages for chapter " + chapterNumber);
            return pageUrls;
            
        } catch (IOException e) {
            Log.e(TAG, "Network error during resolution", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during resolution", e);
            return null;
        }
    }

    /**
     * Fetches chapter list for a manga title.
     */
    public List<ConsumetMangaInfoResponse.Chapter> fetchChapters(String mangaTitle) {
        if (mangaTitle == null || mangaTitle.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            String searchQuery = formatSearchQuery(mangaTitle);
            Response<ConsumetMangaSearchResponse> searchResponse = apiService.searchManga(searchQuery).execute();
            if (!searchResponse.isSuccessful()
                    || searchResponse.body() == null
                    || searchResponse.body().getResults() == null
                    || searchResponse.body().getResults().isEmpty()) {
                return new ArrayList<>();
            }

            String mangaId = searchResponse.body().getResults().get(0).getId();
            Response<ConsumetMangaInfoResponse> infoResponse = apiService.getMangaInfo(mangaId).execute();
            if (!infoResponse.isSuccessful()
                    || infoResponse.body() == null
                    || infoResponse.body().getChapters() == null) {
                return new ArrayList<>();
            }

            return infoResponse.body().getChapters();
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch chapters", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find chapter ID by chapter number from chapters list.
     */
    private String findChapterId(List<ConsumetMangaInfoResponse.Chapter> chapters, int targetChapterNumber) {
        for (ConsumetMangaInfoResponse.Chapter chapter : chapters) {
            try {
                String chapterNumStr = chapter.getChapterNumber();
                if (chapterNumStr != null) {
                    float chapterNum = Float.parseFloat(chapterNumStr);
                    if (Math.abs(chapterNum - targetChapterNumber) < 0.1) {
                        return chapter.getId();
                    }
                }
            } catch (NumberFormatException e) {
                // Skip chapters with non-numeric numbers
            }
        }
        return null;
    }
    
    /**
     * Formats manga title for Consumet search.
     * ISSUE #11 FIX: Use URL encoding instead of aggressive character removal
     */
    private String formatSearchQuery(String title) {
        try {
            return java.net.URLEncoder.encode(title.trim(), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            // Fallback to simple replacement
            return title.trim().replaceAll("\\s+", "%20");
        }
    }
}
