package com.example.mediavault.api.providers;

import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * MangaDex API provider implementation.
 * Direct integration with MangaDex API v5 for manga reading.
 * 
 * This serves as a fallback when Consumet MangaDex is down.
 */
public class MangaDexProvider implements MediaProvider {
    private static final String TAG = "MangaDexProvider";
    private static final String PROVIDER_NAME = "MangaDex";
    private static final String BASE_URL = "https://api.mangadex.org/";
    private static final int TIMEOUT_SECONDS = 15;
    
    private final MangaDexApiService apiService;
    
    public MangaDexProvider() {
        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();
        
        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build();
        
        apiService = retrofit.create(MangaDexApiService.class);
    }
    
    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }
    
    @Override
    public boolean isAvailable() {
        return true; // Let actual calls determine availability
    }
    
    @Override
    public boolean supportsMediaType(MediaType type) {
        return type == MediaType.MANGA;
    }
    
    @Override
    public List<String> resolveMangaChapter(String title, int chapter) {
        Log.d(TAG, "Resolving manga chapter: " + title + " Ch " + chapter);
        
        try {
            // Step 1: Search for manga
            String mangaId = searchMangaId(title);
            if (mangaId == null) {
                Log.e(TAG, "Manga not found: " + title);
                return null;
            }
            Log.d(TAG, "Found manga ID: " + mangaId);
            
            // Step 2: Find the chapter
            String chapterId = findChapterId(mangaId, chapter);
            if (chapterId == null) {
                Log.e(TAG, "Chapter " + chapter + " not found for manga: " + title);
                return null;
            }
            Log.d(TAG, "Found chapter ID: " + chapterId);
            
            // Step 3: Get page URLs
            List<String> pages = getChapterPageUrls(chapterId);
            if (pages == null || pages.isEmpty()) {
                Log.e(TAG, "No pages found for chapter: " + chapterId);
                return null;
            }
            Log.d(TAG, "Got " + pages.size() + " pages");
            
            return pages;
            
        } catch (Exception e) {
            Log.e(TAG, "MangaDex resolution failed", e);
            return null;
        }
    }
    
    @Override
    public List<ChapterInfo> fetchMangaChapters(String title) {
        Log.d(TAG, "Fetching chapters for: " + title);
        
        try {
            // Search for manga
            String mangaId = searchMangaId(title);
            if (mangaId == null) {
                return new ArrayList<>();
            }
            
            // Get chapters
            List<MangaDexApiService.ChapterData> chapters = fetchChaptersWithLanguageFallback(mangaId);
            if (chapters == null || chapters.isEmpty()) {
                return new ArrayList<>();
            }
            
            List<ChapterInfo> result = new ArrayList<>();
            for (MangaDexApiService.ChapterData ch : chapters) {
                MangaDexApiService.ChapterAttributes attrs = ch.getAttributes();
                if (attrs == null) continue;
                
                float chapterNum = 0;
                try {
                    if (attrs.getChapter() != null) {
                        chapterNum = Float.parseFloat(attrs.getChapter());
                    }
                } catch (NumberFormatException ignored) {}
                
                result.add(new ChapterInfo(
                    ch.getId(),
                    chapterNum,
                    attrs.getTitle() != null ? attrs.getTitle() : "Chapter " + attrs.getChapter(),
                    attrs.getPages()
                ));
            }
            
            Log.d(TAG, "Fetched " + result.size() + " chapters");
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch chapters", e);
            return new ArrayList<>();
        }
    }
    
    // ============= HELPER METHODS =============
    
    private String searchMangaId(String title) throws Exception {
        Response<MangaDexApiService.MangaSearchResponse> response = apiService.searchManga(
            title,
            10,
            new String[]{"cover_art"}
        ).execute();
        
        if (!response.isSuccessful() || response.body() == null) {
            return null;
        }
        
        List<MangaDexApiService.MangaData> results = response.body().getData();
        if (results == null || results.isEmpty()) {
            return null;
        }
        
        // Find best match
        String normalizedTitle = normalize(title);
        for (MangaDexApiService.MangaData manga : results) {
            if (manga.getAttributes() != null) {
                String mangaTitle = manga.getAttributes().getTitle();
                if (mangaTitle != null && normalize(mangaTitle).contains(normalizedTitle)) {
                    return manga.getId();
                }
            }
        }
        
        // Return first result if no exact match
        return results.get(0).getId();
    }
    
    private String findChapterId(String mangaId, int targetChapter) throws Exception {
        List<MangaDexApiService.ChapterData> chapters = fetchChaptersWithLanguageFallback(mangaId);
        if (chapters == null) {
            return null;
        }

        String closestChapterId = null;
        float smallestDistance = Float.MAX_VALUE;
        for (MangaDexApiService.ChapterData ch : chapters) {
            if (ch.getAttributes() != null && ch.getAttributes().getChapter() != null) {
                try {
                    float chNum = Float.parseFloat(ch.getAttributes().getChapter());
                    if ((int) chNum == targetChapter) {
                        return ch.getId();
                    }
                    float distance = Math.abs(chNum - targetChapter);
                    if (distance < smallestDistance) {
                        smallestDistance = distance;
                        closestChapterId = ch.getId();
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        return closestChapterId;
    }

    private List<MangaDexApiService.ChapterData> fetchChaptersWithLanguageFallback(String mangaId) throws Exception {
        List<MangaDexApiService.ChapterData> english = fetchMangaChapters(mangaId, "en");
        if (english != null && !english.isEmpty()) {
            return english;
        }
        return fetchMangaChapters(mangaId, null);
    }

    private List<MangaDexApiService.ChapterData> fetchMangaChapters(String mangaId, String language) throws Exception {
        Response<MangaDexApiService.ChapterListResponse> response = apiService.getMangaChapters(
                mangaId,
                500,
                0,
                language,
                "asc"
        ).execute();

        if (!response.isSuccessful() || response.body() == null) {
            Log.e(TAG, "Chapter fetch failed (" + (language == null ? "all-languages" : language) + "): " + response.code());
            return null;
        }
        return response.body().getData();
    }
    
    private List<String> getChapterPageUrls(String chapterId) throws Exception {
        Response<MangaDexApiService.AtHomeResponse> response = apiService.getChapterPages(chapterId).execute();
        
        if (!response.isSuccessful() || response.body() == null) {
            return null;
        }
        
        MangaDexApiService.AtHomeResponse atHome = response.body();
        if (atHome.getChapter() == null) {
            return null;
        }
        
        String baseUrl = atHome.getBaseUrl();
        String hash = atHome.getChapter().getHash();
        List<String> pageFiles = atHome.getChapter().getData();
        
        if (baseUrl == null || hash == null || pageFiles == null) {
            return null;
        }
        
        // Build full URLs
        List<String> urls = new ArrayList<>();
        for (String pageFile : pageFiles) {
            // Format: {baseUrl}/data/{hash}/{filename}
            urls.add(baseUrl + "/data/" + hash + "/" + pageFile);
        }
        
        return urls;
    }
    
    private String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "")
            .trim();
    }
}
