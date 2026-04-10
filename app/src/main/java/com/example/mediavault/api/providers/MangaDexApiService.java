package com.example.mediavault.api.providers;

import com.google.gson.annotations.SerializedName;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * MangaDex API v5 interface.
 * Base URL: https://api.mangadex.org/
 * 
 * Documentation: https://api.mangadex.org/docs/
 */
public interface MangaDexApiService {
    
    /**
     * Search for manga by title.
     */
    @GET("manga")
    Call<MangaSearchResponse> searchManga(
        @Query("title") String title,
        @Query("limit") int limit,
        @Query("includes[]") String[] includes
    );
    
    /**
     * Get manga details by ID.
     */
    @GET("manga/{id}")
    Call<MangaDetailResponse> getMangaDetails(
        @Path("id") String mangaId,
        @Query("includes[]") String[] includes
    );
    
    /**
     * Get chapters for a manga.
     */
    @GET("manga/{id}/feed")
    Call<ChapterListResponse> getMangaChapters(
        @Path("id") String mangaId,
        @Query("limit") int limit,
        @Query("offset") int offset,
        @Query("translatedLanguage[]") String language,
        @Query("order[chapter]") String order
    );
    
    /**
     * Get chapter details including page server info.
     */
    @GET("chapter/{id}")
    Call<ChapterDetailResponse> getChapterDetails(@Path("id") String chapterId);
    
    /**
     * Get chapter page URLs from at-home server.
     */
    @GET("at-home/server/{chapterId}")
    Call<AtHomeResponse> getChapterPages(@Path("chapterId") String chapterId);
    
    // ============= RESPONSE CLASSES =============
    
    class MangaSearchResponse {
        @SerializedName("result")
        private String result;
        
        @SerializedName("data")
        private List<MangaData> data;
        
        @SerializedName("total")
        private int total;
        
        public String getResult() { return result; }
        public List<MangaData> getData() { return data; }
        public int getTotal() { return total; }
    }
    
    class MangaDetailResponse {
        @SerializedName("result")
        private String result;
        
        @SerializedName("data")
        private MangaData data;
        
        public String getResult() { return result; }
        public MangaData getData() { return data; }
    }
    
    class MangaData {
        @SerializedName("id")
        private String id;
        
        @SerializedName("type")
        private String type;
        
        @SerializedName("attributes")
        private MangaAttributes attributes;
        
        public String getId() { return id; }
        public String getType() { return type; }
        public MangaAttributes getAttributes() { return attributes; }
    }
    
    class MangaAttributes {
        @SerializedName("title")
        private TitleMap title;
        
        @SerializedName("altTitles")
        private List<TitleMap> altTitles;
        
        @SerializedName("description")
        private TitleMap description;
        
        @SerializedName("status")
        private String status;
        
        public String getTitle() {
            if (title == null) return null;
            // Try English first, then Japanese, then any available
            if (title.en != null) return title.en;
            if (title.ja != null) return title.ja;
            if (title.jaRo != null) return title.jaRo;
            return null;
        }
        
        public String getStatus() { return status; }
    }
    
    class TitleMap {
        @SerializedName("en")
        public String en;
        
        @SerializedName("ja")
        public String ja;
        
        @SerializedName("ja-ro")
        public String jaRo;
    }
    
    class ChapterListResponse {
        @SerializedName("result")
        private String result;
        
        @SerializedName("data")
        private List<ChapterData> data;
        
        @SerializedName("total")
        private int total;
        
        public String getResult() { return result; }
        public List<ChapterData> getData() { return data; }
        public int getTotal() { return total; }
    }
    
    class ChapterDetailResponse {
        @SerializedName("result")
        private String result;
        
        @SerializedName("data")
        private ChapterData data;
        
        public String getResult() { return result; }
        public ChapterData getData() { return data; }
    }
    
    class ChapterData {
        @SerializedName("id")
        private String id;
        
        @SerializedName("type")
        private String type;
        
        @SerializedName("attributes")
        private ChapterAttributes attributes;
        
        public String getId() { return id; }
        public String getType() { return type; }
        public ChapterAttributes getAttributes() { return attributes; }
    }
    
    class ChapterAttributes {
        @SerializedName("chapter")
        private String chapter;
        
        @SerializedName("title")
        private String title;
        
        @SerializedName("volume")
        private String volume;
        
        @SerializedName("pages")
        private int pages;
        
        @SerializedName("translatedLanguage")
        private String translatedLanguage;
        
        public String getChapter() { return chapter; }
        public String getTitle() { return title; }
        public String getVolume() { return volume; }
        public int getPages() { return pages; }
        public String getTranslatedLanguage() { return translatedLanguage; }
    }
    
    class AtHomeResponse {
        @SerializedName("result")
        private String result;
        
        @SerializedName("baseUrl")
        private String baseUrl;
        
        @SerializedName("chapter")
        private ChapterImageData chapter;
        
        public String getResult() { return result; }
        public String getBaseUrl() { return baseUrl; }
        public ChapterImageData getChapter() { return chapter; }
    }
    
    class ChapterImageData {
        @SerializedName("hash")
        private String hash;
        
        @SerializedName("data")
        private List<String> data;
        
        @SerializedName("dataSaver")
        private List<String> dataSaver;
        
        public String getHash() { return hash; }
        public List<String> getData() { return data; }
        public List<String> getDataSaver() { return dataSaver; }
    }
}
