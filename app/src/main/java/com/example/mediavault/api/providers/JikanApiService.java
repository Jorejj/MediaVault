package com.example.mediavault.api.providers;

import com.google.gson.annotations.SerializedName;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Jikan API v4 interface (MyAnimeList wrapper).
 * Base URL: https://api.jikan.moe/v4/
 * 
 * Note: Jikan provides metadata only, not streaming URLs.
 * Used for anime/manga search and episode lists when Consumet is down.
 * Streaming URLs must be resolved via GogoAnime URL pattern extraction.
 * 
 * Rate limit: 3 requests/second
 */
public interface JikanApiService {
    
    /**
     * Search for anime by title.
     */
    @GET("anime")
    Call<AnimeSearchResponse> searchAnime(
        @Query("q") String query,
        @Query("limit") int limit
    );
    
    /**
     * Get anime details by MAL ID.
     */
    @GET("anime/{id}")
    Call<AnimeDetailResponse> getAnimeDetails(@Path("id") int malId);
    
    /**
     * Get anime episodes.
     */
    @GET("anime/{id}/episodes")
    Call<EpisodeListResponse> getAnimeEpisodes(
        @Path("id") int malId,
        @Query("page") int page
    );
    
    /**
     * Search for manga by title.
     */
    @GET("manga")
    Call<MangaSearchResponse> searchManga(
        @Query("q") String query,
        @Query("limit") int limit
    );
    
    /**
     * Get manga details by MAL ID.
     */
    @GET("manga/{id}")
    Call<MangaDetailResponse> getMangaDetails(@Path("id") int malId);
    
    // ============= RESPONSE CLASSES =============
    
    class AnimeSearchResponse {
        @SerializedName("data")
        private List<AnimeData> data;
        
        @SerializedName("pagination")
        private Pagination pagination;
        
        public List<AnimeData> getData() { return data; }
        public Pagination getPagination() { return pagination; }
    }
    
    class AnimeDetailResponse {
        @SerializedName("data")
        private AnimeData data;
        
        public AnimeData getData() { return data; }
    }
    
    class AnimeData {
        @SerializedName("mal_id")
        private int malId;
        
        @SerializedName("title")
        private String title;
        
        @SerializedName("title_english")
        private String titleEnglish;
        
        @SerializedName("title_japanese")
        private String titleJapanese;
        
        @SerializedName("type")
        private String type;
        
        @SerializedName("episodes")
        private Integer episodes;
        
        @SerializedName("status")
        private String status;
        
        @SerializedName("score")
        private Float score;
        
        @SerializedName("synopsis")
        private String synopsis;
        
        @SerializedName("images")
        private Images images;
        
        public int getMalId() { return malId; }
        public String getTitle() { return title; }
        public String getTitleEnglish() { return titleEnglish; }
        public String getTitleJapanese() { return titleJapanese; }
        public String getType() { return type; }
        public Integer getEpisodes() { return episodes; }
        public String getStatus() { return status; }
        public Float getScore() { return score; }
        public String getSynopsis() { return synopsis; }
        public Images getImages() { return images; }
        
        public String getBestTitle() {
            if (titleEnglish != null && !titleEnglish.isEmpty()) {
                return titleEnglish;
            }
            return title;
        }
    }
    
    class EpisodeListResponse {
        @SerializedName("data")
        private List<EpisodeData> data;
        
        @SerializedName("pagination")
        private Pagination pagination;
        
        public List<EpisodeData> getData() { return data; }
        public Pagination getPagination() { return pagination; }
    }
    
    class EpisodeData {
        @SerializedName("mal_id")
        private int malId;
        
        @SerializedName("title")
        private String title;
        
        @SerializedName("title_japanese")
        private String titleJapanese;
        
        @SerializedName("title_romanji")
        private String titleRomanji;
        
        @SerializedName("aired")
        private String aired;
        
        @SerializedName("filler")
        private boolean filler;
        
        @SerializedName("recap")
        private boolean recap;
        
        public int getMalId() { return malId; }
        public String getTitle() { return title; }
        public String getTitleJapanese() { return titleJapanese; }
        public String getTitleRomanji() { return titleRomanji; }
        public String getAired() { return aired; }
        public boolean isFiller() { return filler; }
        public boolean isRecap() { return recap; }
    }
    
    class MangaSearchResponse {
        @SerializedName("data")
        private List<MangaData> data;
        
        @SerializedName("pagination")
        private Pagination pagination;
        
        public List<MangaData> getData() { return data; }
        public Pagination getPagination() { return pagination; }
    }
    
    class MangaDetailResponse {
        @SerializedName("data")
        private MangaData data;
        
        public MangaData getData() { return data; }
    }
    
    class MangaData {
        @SerializedName("mal_id")
        private int malId;
        
        @SerializedName("title")
        private String title;
        
        @SerializedName("title_english")
        private String titleEnglish;
        
        @SerializedName("title_japanese")
        private String titleJapanese;
        
        @SerializedName("type")
        private String type;
        
        @SerializedName("chapters")
        private Integer chapters;
        
        @SerializedName("volumes")
        private Integer volumes;
        
        @SerializedName("status")
        private String status;
        
        @SerializedName("score")
        private Float score;
        
        @SerializedName("synopsis")
        private String synopsis;
        
        @SerializedName("images")
        private Images images;
        
        public int getMalId() { return malId; }
        public String getTitle() { return title; }
        public String getTitleEnglish() { return titleEnglish; }
        public String getTitleJapanese() { return titleJapanese; }
        public String getType() { return type; }
        public Integer getChapters() { return chapters; }
        public Integer getVolumes() { return volumes; }
        public String getStatus() { return status; }
        public Float getScore() { return score; }
        public String getSynopsis() { return synopsis; }
        public Images getImages() { return images; }
        
        public String getBestTitle() {
            if (titleEnglish != null && !titleEnglish.isEmpty()) {
                return titleEnglish;
            }
            return title;
        }
    }
    
    class Images {
        @SerializedName("jpg")
        private ImageUrls jpg;
        
        @SerializedName("webp")
        private ImageUrls webp;
        
        public ImageUrls getJpg() { return jpg; }
        public ImageUrls getWebp() { return webp; }
        
        public String getImageUrl() {
            if (jpg != null && jpg.getLargeImageUrl() != null) {
                return jpg.getLargeImageUrl();
            }
            if (webp != null && webp.getLargeImageUrl() != null) {
                return webp.getLargeImageUrl();
            }
            return null;
        }
    }
    
    class ImageUrls {
        @SerializedName("image_url")
        private String imageUrl;
        
        @SerializedName("small_image_url")
        private String smallImageUrl;
        
        @SerializedName("large_image_url")
        private String largeImageUrl;
        
        public String getImageUrl() { return imageUrl; }
        public String getSmallImageUrl() { return smallImageUrl; }
        public String getLargeImageUrl() { return largeImageUrl; }
    }
    
    class Pagination {
        @SerializedName("last_visible_page")
        private int lastVisiblePage;
        
        @SerializedName("has_next_page")
        private boolean hasNextPage;
        
        @SerializedName("current_page")
        private int currentPage;
        
        public int getLastVisiblePage() { return lastVisiblePage; }
        public boolean hasNextPage() { return hasNextPage; }
        public int getCurrentPage() { return currentPage; }
    }
}
